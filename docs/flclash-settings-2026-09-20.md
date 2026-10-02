# FlClash 配置对照与本轮 APK

范围：参考 FlClash Android 的 General、Network、DNS、Advanced 设置，保留 MikuBox 原有 UI / VPN / Core / Advanced 分类，设置直接进入 Mihomo 或 Android VpnService。此表不是完整对齐声明。

参考源码：[General](https://github.com/chen08209/FlClash/blob/main/lib/views/config/general.dart)、[Network](https://github.com/chen08209/FlClash/blob/main/lib/views/config/network.dart)、[DNS](https://github.com/chen08209/FlClash/blob/main/lib/views/config/dns.dart)。源码路径以仓库当前目录为准；本轮比对副本保存在本机 /tmp/flclash-settings。

| 配置能力 | MikuBox 本轮状态 | 位置/实现 |
| --- | --- | --- |
| 日志、混合端口、允许局域网 | 已接入 | Core，Mihomo 原生字段 |
| TCP 并发、统一延迟 | 已接入 | Advanced，核心实际配置测试 |
| 查找进程 | 已接入，Android 14 独立应用包名规则实测通过 | Advanced；API 29+ 查询 UID/包名，测试使用 AND(PROCESS-NAME,DST-PORT)，未命中会 REJECT |
| Geo 低内存加载 | 已接入 | Advanced，memconservative |
| TLS 检验、客户端指纹 | 已接入 | Advanced；TLS 策略同时作用于节点和 provider override，自签名证书拒绝/接受测试 |
| Keep-alive | 已接入 | Advanced |
| Hosts、UA、代理认证 | 本轮新增 | Core；原生 hosts/global-ua/authentication |
| 外部控制器地址与密钥 | 已接入 | Advanced |
| 延迟测试 URL | 保留 | Advanced |
| TUN 栈 | 已接入 | VPN；修正 Android 接口地址与核心地址不一致 |
| DNS 劫持 | 已接入 | VPN；支持 UDP/TCP 条目和显式空列表 |
| 路由模式、自定义 CIDR | 已接入 | VPN；通过 Android 路由表执行 |
| 按应用代理、VPN DNS/MTU/接口地址、HTTP 系统代理 | 保留 | VPN |
| Keep Awake | 保留 | Android 唤醒锁，不属于 Xray 配置 |
| DNS 模式、Fake-IP 范围/过滤、nameserver/default/proxy/direct/fallback | 已接入 | Core |
| DNS Hosts/system-hosts、Prefer-H3、respect-rules、IPv6 | 已接入 | Core |
| nameserver-policy、fallback GeoIP/IP-CIDR | 本轮新增 UI | Core |
| fallback-filter 的 geoip-code/geosite/domain | 已补独立 UI 与原生配置 | Core |
| 追加系统 DNS | 已补 UI 与执行链路 | 取 NOT_VPN 底层网络 DNS，监听网络更新 |
| VPN Allow bypass 开关 | 已补 UI | 对接 Android VpnService.Builder |
| 系统 HTTP 代理绕过域名 | 已补 UI | 对接 Android ProxyInfo exclusion list |
| 独立 IPv6 入站开关 | 已拆分 | 未设置时继承全局 IPv6，设置后独立控制 TUN 地址与路由 |
| 按网络按需连接 | 已实现并通过运行验证 | VPN；网络类型开关、SSID 排除、前台监听、手动断开保持、备份恢复 |
| 全局配置覆写脚本 | 已实现并通过 JNI 与真实流量验证 | Advanced；编辑、预览、校验保存、开关、异常/超时处理；main(config) |
| 脚本库管理、按配置绑定 | 已实现 | Advanced；新建/编辑/重命名/删除/预览、专用/继承/禁用绑定、备份恢复与订阅更新保持 |
| 桌面系统代理、macOS 系统 DNS、桌面出口网卡选择 | Android 不适用对应桌面操作 | FlClash 自身按平台隐藏 |

## APK 边界

本轮先交可安装测试包。快捷开关使用 MikuRay 的通知图标与连接中/已连接/断开中状态；计时从服务真正连接时开始，以单调时钟计算。分类预览与实际内容同步调整。

TUN 文件描述符采用显式复制：Java 始终关闭它创建的原始描述符，Go listener 只持有复制件；初始化失败也不会双方关闭同一个编号。该补丁通过 Go overlay 编译，不修改 Mihomo submodule 的提交。

本表列出的 Android 功能缺项已补齐；“已实现”不代表所有设备和协议均已实测。最终收尾结果与验证边界见下方报告。

## 上一版交付验证（仅启动验证，不能证明 mixed 数据链路正常）

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 成功；30 项测试零失败，Lint 0 errors / 225 warnings。
- `go test -tags 'with_gvisor cmfa' ./...` 成功，包含真实 TLS 握手与核心配置验证。
- 最终 arm64 APK 通过 apksigner 校验；SHA-256：`b01b6fc036dbbfcc98a23ab089eb0b9968525838ed44e70467e8f2e3af3c2670`。
- Android 16 模拟器安装最终 APK，Mixed TUN 使用 `10.10.14.1/30` 成功启动，主页显示已连接并有流量；点击断开后回到未连接，进程存活，crash buffer 为空。
- 最终 APK 未做真机验证、所有代理协议验证、进程规则实际流量验证或快捷面板完整交互验证。计时覆盖了单调时钟回归测试，未完成真机 UI 验证。
- 本轮临时偏好、显示缩放和 ADB 转发已恢复/移除。

## 后续修复

Mixed 实际 TCP 数据链路修复、补充配置与新 APK 的验证见 [后续修复报告](followup-2026-09-20.md)。以上旧包的“成功启动”不代表直连可用；后续已用独立应用 UID 复现旧包故障并验证修复。

按需连接与脚本的实现、验证和边界见 [本轮报告](automation-2026-09-20.md)。

脚本库、配置绑定、后台与重启验证，以及重复连接的 TUN 缓存修复见 [收尾报告](completion-2026-09-20.md)。
