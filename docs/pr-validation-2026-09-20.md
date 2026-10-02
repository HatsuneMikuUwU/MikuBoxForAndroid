# 提交前最终检查（2026-09-20）

本次将原 PR #21 的 8 个 UI 移植提交与后续本地修复一并提交到替代 PR。当前说明以 mihomo 原生设置和最新模式交互为准。

- JDK 21：`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 成功。
- Android 单元测试 27 项，0 失败、0 错误、0 跳过。
- Lint：0 错误、224 警告，警告尚未全部整改。
- Go：`go test -tags 'with_gvisor cmfa' ./...` 通过。
- arm64-v8a、armeabi-v7a、x86_64 Debug APK 均通过 apksigner 签名验证。
- 本轮仅重新执行上述自动检查。此前在最终功能迭代中的 Android 16 arm64 模拟器验证见同目录的 inspection、mihomo-settings、mode-switch 和 onboarding 报告；本轮不将其记为全部重新实测。
- 构建产物、日志、截图不提交 Git；没有新增私钥、令牌或本地测试配置。

## APK SHA-256

- `app-arm64-v8a-debug.apk`：`ca3dbc30978268ffa6cb35231cde21daeb442233fddc502b56b3f7cd06c9be88`
- `app-armeabi-v7a-debug.apk`：`e9b977a643be498ca7715d0256aee7376fdffd6ef422900b3c2d884a98819c4c`
- `app-x86_64-debug.apk`：`476cdcd2539972326564b440b84a227a987061d999097aab8db63aabef0847b5`
