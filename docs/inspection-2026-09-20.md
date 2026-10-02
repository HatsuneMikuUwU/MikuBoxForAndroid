# MikuRay UI / mihomo 功能接缝检查（2026-09-20）

基线：Mihomo 与 MikuRay UI 整合版本。UI 来源为 https://github.com/HatsuneMikuUwU/MikuRay 。本次关注应用的数据通路、配置转换、服务生命周期和最终 Android 包；不是对 vendored mihomo 全仓库的穷尽安全审计。

## 已修复的功能问题

| 级别 | 复现条件 / 原行为 | 修复 |
|---|---|---|
| P1 | 文件、剪贴板、扫码导入经 AngConfigManager 写入 MMKV，而服务只读 MihomoProfileStore；重启后 UI 记录会被镜像清理 | 增加 MikuProfiles 接缝。原生 YAML/JSON、支持的分享链接、订阅地址导入真实配置仓库；整份配置保留规则、组、提供器 |
| P1 | 节点编辑只写 MMKV；单节点镜像没有完整认证字段，协议表单不能安全回写 | 现有配置编辑与手动创建改走原有自定义配置编辑页面，读写完整 mihomo YAML/JSON，保存前检查 YAML 语法。不会从展示字段重新生成配置 |
| P1 | 删除只删除镜像，重启后复活；选择只写 MMKV，重启后丢失 | 单条/批量删除和选择同步到真实仓库；主页恢复、订阅变更刷新镜像 |
| P1 | 设备点击“分享备份”导致进程被 CrashHandler 杀死，VPN 随之断开：UI 使用 `.cache` authority，清单只有 `.fileprovider`，且只允许 logs 子目录 | 补齐兼容 authority 和缓存文件路径；修复包已打开系统分享面板，VPN 保持运行且 HTTP 请求仍成功 |
| P1 | 备份页只备份 MMKV，恢复后没有实际 mihomo 配置和订阅 | 在原备份容器加入 mihomo 配置/设置/流量数据；恢复前预检该数据；旧的仅 UI 备份明确拒绝，避免假恢复成功 |
| P1 | 新建订阅关闭自动更新仍被强制为 15 分钟；调度器及 Worker 也把 0 当 15 分钟 | 0 保持禁用；调度器及执行器都过滤禁用记录；启动时恢复调度 |
| P1 | 未下载的订阅默认含 MATCH,DIRECT，连接可能呈现成功但实际全直连 | 未下载订阅保存空配置，服务启动时拒绝并提示先更新订阅 |
| P1 | “通过代理更新”在未连接或旧端口值为 0 时静默直连 | 使用当前 UI 的真实混合端口；未连接时明确失败，保留旧配置 |
| P1 | 关闭全部原生规则后将原规则加回；配置读取依赖逐行字符串扫描 | 使用完整开关列表判断规则来源；SnakeYAML 解析顶层 rules，支持引号、注释、无缩进序列、flow style 和 JSON |
| P1 | AND/OR 子条件错误携带出口；自定义出口被强制大写 | 仅最外层加出口，保留命名出口大小写；Go 集成测试验证组合规则实际拦截 |
| P1 | 国家批量测试把单例 mihomo 当临时 Xray 实例启动并在 finally 停止 | 禁止该 worker 操作单例 VPN；UI 明确说明整份配置不支持批量真实延迟/国家测试，保留连接后的真实测试与 TCP 探测 |
| P2 | 连接异步启动却立即检查 running，误报未启动；重启通过 stopSelf 后立即 start 引入销毁竞争 | 返回“请求已受理”，最终结果以服务广播为准；重启使用现有服务的 ACTION_RESTART；忽略已经连接时的重复启动 |
| P2 | 当前连接延迟取序列化后的第一个策略组，通常可能是 GLOBAL/DIRECT | 改用 HTTP 混合入口请求测试地址，遵循实际规则，失败返回 -1 |
| P2 | 外部分享链接被当成 content URI 读取，导入失败且无提示 | 已支持的协议链接交给配置解码器；导入完成刷新列表，失败显示原因 |
| P2 | 镜像只比较名称、协议、地址，端口/TLS 等变更未刷新；未保存原始配置 | 比较协议字段及名称等展示元数据（ProfileItem.equals 不比较名称），同步原始配置；刷新分组缓存，分享当前配置使用原生内容 |

新增单元回归覆盖禁用自动更新、本地配置非订阅、配置 CRUD/选择/备份往返、无效配置不覆盖、JSON/YAML 保真、备份预检不写入、全关闭规则、不同 YAML 形态及嵌套逻辑规则。

## 静态检查追加修复

完整 Lint 在基线移植代码中发现 29 个错误（不沿用 9 月 18 日旧报告）：VPN `setHttpProxy` 的 API 29 判断反向，日志/Root 工具使用 API 26 才提供的 Process 方法，RenderEffect 调用缺少 API 31 保护，启动页旧返回键覆盖、重复启动提前返回未调用 super，以及 RecyclerView 回调捕获旧位置等。已修正版本保护、兼容轮询等待、返回键处理和点击时定位；图片组件采用 AppCompat 基类，修正容易误读的缩进。天气位置读取已有权限检查和 SecurityException 处理，仅对该函数补充 Lint 说明；为保持原 UI 的 HCT 配色，两个使用固定 Material 版本内部颜色算法的类明确记录 RestrictedApi 例外，未全局关闭检查。

## 后续进度与剩余限制

以下是源代码确认的剩余适配工作，不应因页面能打开就记为通过：

- **逐节点/策略组控制**：主页目前以完整配置为一行。后续已补充主页全局模式的组/节点出口选择（见 `mode-switch-2026-09-20.md`）；规则模式下配置内部各策略组的成员切换界面仍需单独适配。
- **核心设置已完成后续替换**：当前直接编辑 mihomo 原生参数，删除无对应能力的 Xray 设置及旧映射层，保留原设置分类和 Keep Awake 等 Android 功能。路由入口改为编辑原生 YAML/JSON，早期逐条规则转换实现及相关测试已移除。以 `mihomo-settings-2026-09-20.md` 为准。
- **更新检查**：`AppConfig.APP_API_URL` 仍指向 MikuRay；`UpdateCheckerManager.compareVersions` 直接对点分段调用 `toInt()`，不支持本项目 `UwU-1.0.0`。需要独立的 mihomo 发布渠道及版本约定，不能简单替换 URL 后把原项目旧内核包当升级包。
- **批量配置导出与加密组文件**：这些功能原本面向 MikuRay 节点/组模型，尚未完成对整份 mihomo 配置的往返验证。完整迁移请使用此次接线的“备份与恢复”。
- **协议覆盖**：本次运行验证不等于 VLESS/Reality、VMess、Trojan、SS、HY2、TUIC、WireGuard 的真实服务端兼容性测试。分享链接转换器也不是原生配置所有字段的无损映射，复杂配置应直接导入 YAML/JSON。
- **持久化边界**：SharedPreferences 和 MMKV 不是同一个事务；预检防止格式错误造成部分写入，但磁盘错误中途发生时仍不具备跨仓库原子恢复。

## 验证

使用 JDK 21；系统默认 JDK 25 不兼容当前 Gradle/Kotlin 配置。执行命令：

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
cd core/mihomo-bridge
go test -count=1 -tags 'with_gvisor cmfa' ./...
```

本次执行结果：

- Android 单元测试 26 项全部通过；完整 Lint 为 0 错误、221 警告，警告尚未全部整改。
- mihomo Go 桥集成测试通过，包含真实混合 HTTP 入口成功转发与 AND/OR 规则拒绝请求；不是仅验证生成字符串。
- Debug 的 arm64-v8a、armeabi-v7a、x86_64 APK 均构建成功，签名和打包的 mihomo/JNI 库检查通过。设备运行只覆盖 Android 16/API 36 arm64 模拟器；未宣称其余 ABI、旧 Android 或 Release 设备运行通过。
- 设备完成 SOCKS5 分享链接导入、系统文件选择器 YAML 导入、完整配置编辑并写入原生仓库、连接、gVisor TUN 建立、真实延迟及流量显示。最终包确认编辑后的名称显示正确；删除本次两个测试配置、强制停止并重启后，仅保留原有配置，没有复活。
- 通过 adb 转发访问应用混合入口，HTTP 返回 `mihomo-audit-ok`；日志确认该 LAN 请求按 GeoIP(lan) 走 DIRECT，公网测试地址按 MATCH 走 `PROXY[AuditSOCKS]`。LAN 请求不作为跨应用 TUN 转发的证明，浏览器端到端测试尚未完成。
- 分享备份已出现系统 chooser，未实际发送文件；分享后混合入口请求仍成功，未产生新的 CrashHandler 日志。备份内容含完整原生配置及 8 类原生存储；恢复往返有单元测试，系统文件选择器恢复未作设备验证。
- 点击停止后显示 Disconnected；设备直连 127.0.0.1:10808 返回 Connection refused。此项证明本次 TCP 入口关闭，不替代全部 TCP/UDP 端口及反复启停压力测试。
- 编译与模拟器同时运行时曾出现启动超时/ANR，停止高负载构建后启动成功（约 3.3 秒，最终清理后的冷启动 2.963 秒）；未据此认定所有冷启动场景稳定。

本次日志、截图、APK SHA-256 和签名检查结果保存在 `app/build/reports/audit-2026-09-20/`（构建产物，不进入 Git）。本节记录当时的验证；最终提交前检查以 `pr-validation-2026-09-20.md` 为准，不将历史设备测试描述为本轮重新执行。
