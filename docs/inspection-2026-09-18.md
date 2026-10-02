# 2026-09-18 检查与修复

本次在已有 44 个修改文件的工作区上检查并增量修复，保留原有修改和根目录测试 APK。范围为 Android 应用层、服务生命周期及 Go/JNI 桥接入口；未对 vendored Mihomo 全仓库进行穷尽审计。

## 已修复

| 问题 | 影响与处理 |
| --- | --- |
| 周期订阅任务先取消再更新 | 下载成功写回配置也会触发重新调度，从而取消正在执行的任务。仅在无订阅时取消；其余使用 UPDATE，内容刷新不重建调度。 |
| 下载使用旧配置对象覆盖整条记录 | 下载期间的名称、置顶等修改可能丢失。持锁读取最新记录，只更新内容与时间；源地址、原内容或更新时间变化以及记录已删除时拒绝过期写入。 |
| 节点流量跨配置混入 | 未连接的配置也叠加了当前核心流量。仅为当前会话所属配置叠加实时增量。 |
| 清零后历史流量重新出现 | 清零持久化计数时未重置运行中会话基线。现在在同一锁内重置总量与节点基线。 |
| 损坏备份造成部分覆盖 | 逐个配置仓库解析并立即提交，后续解析失败时前面的设置已经修改。现在检查版本、解析所有条目并拒绝未知类型，然后才提交；检查写入结果。磁盘错误时多个 SharedPreferences 文件仍不具有跨文件事务性。 |
| 停止服务后代理端口仍监听 | 模拟器与新增 Go 测试均复现：上游 Shutdown 仅清理 TUN。桥接层显式关闭内置代理、定制入站、转发隧道、DNS 监听与已建立连接；错误启动也清理残留监听。 |
| 两个服务争用单一核心 | VPN 和本地代理使用独立队列，旧服务的停止可能关闭新服务核心。统一到进程级队列，记录核心归属，忽略过期服务的停止；切换前先结算旧会话。 |
| 启动完成后覆盖停止状态 | 启动期间停止或销毁服务后，后台任务仍可能发布 running、唤醒锁和定时器。引入启动代次检查，跳过过期任务，并在主线程发布状态。 |
| 启动期间切换配置导致流量归属错误 | 使用启动时选中的配置快照建立流量会话，而非启动完成后的当前选中项。 |
| 合法直连配置无法导入 | 模拟器文件导入复现：识别条件只接受代理节点字段。改为识别 Mihomo 配置键，支持只有规则或 DNS 的配置，并排除仅在注释中出现代理关键词的文本。 |
| 重排序输入重复 ID | 重复 ID 会复制配置记录；重排前去重。 |

## 验证

使用本机 Temurin JDK 21。默认 JDK 25 下 Gradle 配置失败，本次未改变用户的系统 Java 设置。

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
cd core/mihomo-bridge
go test -tags 'with_gvisor cmfa' ./...
```

测试入口：`app/src/test/java/com/mikubox/mihomo/RegressionTest.kt`。

结果：

- 10 项 Robolectric/JUnit 回归测试通过，0 失败、0 跳过。
- Go bridge 测试通过；新增 TCP/UDP 停止与重复启动回归测试，修复前明确失败（停止后仍接受 TCP 连接），修复后通过。
- Android Lint 为 0 错误、639 条警告；主要为未使用资源、不可翻译资源覆盖、KTX 建议和无障碍描述，尚未全部清理。
- arm64-v8a、armeabi-v7a、x86_64 Debug APK 构建成功，签名校验通过，每个 APK 包含两份对应架构的原生库。
- arm64 Android 模拟器安装最终 APK，系统文件选择器导入仅直连规则配置成功；VPN 授权、连接与断开成功。日志确认 gVisor TUN 启动及 DIRECT 流量，截图记录上下行计数。
- 连续 5 次快速启动/停止，界面最终保持断开；本地代理切换至 VPN 后 TUN 正常工作，VPN 切换至本地代理后 127.0.0.1:7890 正常监听；最初复查发现停止后端口残留，补充桥接层修复后在最终 APK 上验证 TCP/UDP 7890 均释放。最终包再次连接并执行 5 次快速启停，最终 UI 断开、无处于 UP 状态的 TUN、无代理监听、无崩溃。
- 应用崩溃缓冲区为空。模拟器冷启动期间曾出现 System UI 无响应，等待恢复后继续测试；此事件不是 MikuBox 崩溃。
- 未执行真机、真实订阅服务、可选外部控制器和全部代理协议测试；多文件备份写入不提供磁盘故障时的跨文件原子性。

本地证据（构建目录，不纳入版本控制）：

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/smoke/connected.png`
- `app/build/reports/smoke/logcat.txt`
- `app/build/reports/smoke/crash-buffer.txt`
- `app/build/reports/smoke/apks.json`

根目录原有 `MikuBox-test-arm64.apk` 未替换，新包在 `app/build/outputs/apk/debug/`。
