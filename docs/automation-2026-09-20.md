# 按需连接与配置覆写脚本

## 配置入口与行为

保留原有分类：VPN 设置新增“按需连接”，高级设置新增“配置覆写脚本”，首页分类预览同步更新。

### 按需连接

- 首次开启先检查已下载配置及 Android VPN 授权；取消授权不会开启开关。
- 可独立允许 Wi-Fi、移动网络、以太网和其他物理网络；只监听 NOT_VPN 网络，避免自身 VPN 导致重连循环。
- 可按完整、区分大小写的 SSID 排除 Wi-Fi，每行一个；Android 的 SSID 长度按 UTF-8 字节校验。
- 只有使用 SSID 排除时才需要定位权限。Android 10+ 后台读取需要始终允许位置，设备定位也需开启。无法读取名称时暂停，而不是误连排除网络。
- 前台监听服务在隧道暂停时仍运行，网络变化后继续评估。通知有“停用并断开”；关闭设置开关只停止自动管理，保留当前连接。
- 用户手动断开后，同一网络状态不会被监听器立即重新连接；下一次网络变化或修改自动连接设置后重新评估。
- 启动失败不进行无限重试；被系统强制停止后须重新打开应用。重启后是否运行仍遵守原来的开机连接开关。
- 设置已加入完整备份和恢复。

### 配置覆写脚本

- 提供全局 JavaScript 编辑、预览、校验保存、启用/停用。预览基于当前已下载配置，不启动或中断 VPN。
- 入口为 `main(config)`，返回配置对象；支持函数声明、箭头函数及能够立即完成的 Promise / async 函数。返回 undefined/null 保持原配置。
- 执行顺序：导入配置 → 脚本 → 界面设置覆写 → Android 管理的 TUN 描述符/路由字段。脚本不能破坏应用对 TUN 的所有权。
- 不暴露 Java/Go 对象、文件、网络、定时器 API。每次执行新建 JS runtime，2 秒超时，限制调用栈、源代码和输出尺寸；没有独立进程或硬性 JS 堆上限，仅用于用户自行编写/审阅的本地脚本。
- 保存前执行校验；语法错误、抛异常、循环超时、非对象返回、循环引用及未完成 Promise 均报告错误。启用脚本在实际连接时失败，会停止启动并报告原因，不静默忽略。
- 原始订阅内容不会被脚本覆盖；每次连接从原始配置重新执行。脚本和开关保存在已有的完整备份中。
- 当前是一个全局脚本；FlClash 的脚本库管理与按配置单独绑定尚未对齐。

示例：

```javascript
function main(config) {
  config.rules = ["DOMAIN,example.com,DIRECT", ...(config.rules || [])];
  return config;
}
```

## 参考与依赖

本轮查看 FlClash 提交 `c7be7023d33615cb624148d41414f80a7d96cede` 的 [按需连接页面](https://github.com/chen08209/FlClash/blob/c7be7023d33615cb624148d41414f80a7d96cede/lib/views/config/on_demand.dart) 与 [脚本执行契约](https://github.com/chen08209/FlClash/blob/c7be7023d33615cb624148d41414f80a7d96cede/plugins/rust_api/rust/src/script/mod.rs)。实现为原生 Kotlin/Go，不引入 Flutter/Rust UI。

JavaScript 引擎使用 [Goja](https://github.com/dop251/goja)，锁定版本 `v0.0.0-20260917113740-793a2a65c13b`；桥接与 CI 的 Go 最低版本同步升至 1.25。

## 验证

- Android 单元测试 33 项通过，新增网络判断、SSID 边界、脚本开关生成与备份往返测试。
- Go 回归通过，覆盖脚本同步/async 变换、错误返回、异常、循环超时、运行时隔离、原配置不变。
- Android 14 arm64 模拟器安装最终 APK，MIXED 下独立 test APK 双向 393216 字节直连与代理校验通过。
- 原配置最终规则为 REJECT，脚本加入 AND(PROCESS-NAME, DST-PORT) 的 DIRECT 规则；独立 test APK 只有命中自身包名才可访问直连 fixture，因此同时证明脚本实际生效与进程查找的真实流量路径。
- JNI 预览及无限循环 2 秒中断通过；前台监听服务自动连接、禁用网络后断开、重新允许后连接、手动断开后保持断开，全部通过。
- 已检查按需连接、脚本编辑与结果预览的中文界面截图；预览展示新增规则且保持原连接。
- UI 实际编辑 SSID 排除：加入模拟器当前 AndroidWifi 时通知显示暂停且无 VPN；改为 UnlistedWifi 后出现“已连接”VPN 通知。定位权限已在临时模拟器授予。
- Android 构建、33 项单元测试通过；Lint 0 errors / 232 warnings（新增提示为 KTX 风格建议）。Go 回归通过。未做实体手机网络切换、厂商后台限制或重启后启动验证。
- 证据保存于 `app/build/reports/automation-2026-09-20/`。

交付 APK：`app/build/outputs/delivery/MikuBox-20260920-automation-arm64-v8a.apk`；SHA-256：`d378402ae178a892e99b8e3d60d270a9b2a74a51bdfa1ce7be8e6dbfcda0701e`。沿用上一轮 mixed 修复包的新 debug 签名。

## 后续收尾

本报告记录单一全局脚本阶段；后续已补充脚本库、按配置绑定和模拟器重启验证，见 [收尾报告](completion-2026-09-20.md)。
