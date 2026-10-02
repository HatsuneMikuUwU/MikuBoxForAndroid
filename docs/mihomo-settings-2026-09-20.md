# mihomo 原生设置

核心设置页直接编辑 `MihomoCoreSettings`、`CoreOverrides` 和 `DnsOverrides`，输出 mihomo 原生配置字段。UI 模块只负责主题、布局和入口；不再通过 Xray MMKV 配置转换核心参数。

- 原生选项：`mixed-port`、`allow-lan`、`ipv6`、`log-level`、`unified-delay`、`tcp-concurrent`、`tun.stack`、进程匹配、Geodata、Keep Alive、`sniffer` 和 `dns`。
- DNS 使用 `nameserver`、`default-nameserver`、`proxy-server-nameserver`、`direct-nameserver`、`fallback`、`enhanced-mode`、Fake IP 过滤和原生开关。
- 移除 Fragment 页面/入口/搜索索引、Xray 嗅探策略/远程与国内 DNS/旧 DNS 解析策略等旧核心控件，以及 Hev、Root、WebSocket 心跳等不适用的设置。
- 保留原核心设置、VPN 设置、高级设置分类。IPv6、DNS 增强/Fake IP、TUN、Keep Alive 放回 VPN 设置；嗅探、解析服务器、端口和日志留在核心设置。
- 恢复 Keep Awake（保留旧偏好并支持运行时开关唤醒锁）、动态端口（同一会话共享实际监听端口）、资源下载源等与核心实现无关的功能。
- Android VPN 的分应用、MTU、接口地址、路由绕过和系统 HTTP 代理独立保留，由 `AndroidVpnSettings` 提供给 `VpnService.Builder`。
- 原有 mihomo 原生偏好继续使用。旧 Xray 偏好不迁移、不再覆盖核心；无需清除用户配置文件。
- 核心设置修改后重新连接生效；主界面模式切换仍即时生效。DNS/嗅探等“跟随配置”选项不输出对应覆盖字段。混合端口默认 10808；启用动态端口时每次建立连接选择一个随机端口，供核心和应用内请求共同使用。

路由入口也改为直接编辑当前 mihomo YAML / JSON。旧路由转换/同步代码已删除，MMKV 数据不再注入启动配置。这修复了 DIRECT-only 配置因旧页面预置 `GEOSITE,google,PROXY` 引用不存在的代理组而无法启动的问题；旧的 Xray 自定义路由不再自动转换，需要按 mihomo 语法写入配置。

## 验证

- 最终 `testDebugUnitTest`、`lintDebug`、`assembleDebug` 全部通过：27 项测试，0 失败；lint 0 错误、224 警告。移除了 6 项已退休的 Xray 路由转换测试，补充原生参数与不注入旧路由的回归测试。
- arm64-v8a、armeabi-v7a、x86_64 三份 APK 签名验证通过。
- Android 16 arm64 模拟器：原核心/VPN 分类显示正确，Keep Awake、动态端口可见；路由入口打开当前 mihomo 配置。
- 通过 UI 将端口改为 10809，读取到原生存储值；DIRECT-only 配置成功启动，10809 代理 HTTP 请求返回受控内容，旧 10808 不再提供代理。
- VPN 运行中开关 Keep Awake，`dumpsys power` 分别确认取得/释放 `PARTIAL_WAKE_LOCK`，无需断开 VPN。
- 动态端口实际监听 34733，HTTP 代理请求通过；原固定端口恢复为 10808，动态端口和 Keep Awake 恢复关闭，VPN 已断开，临时 HTTP 服务和 adb 转发已清理。
- 证据位于 `app/build/reports/native-settings-2026-09-20/`。未在实体设备验证。
