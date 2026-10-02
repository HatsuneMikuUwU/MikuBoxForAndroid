# 首次启动引导

继续使用 MikuRay 的三页引导布局、插画、指示圆点及“下一页 / 跳过 / 开始”交互。与上游 `master` 的 `uwu_activity_welcome.xml` 比较一致；应用资源已覆盖为 MikuBox 名称和 mihomo 介绍，保留现有多语言文案。

本次将引导完成标记从旧的 `pref_welcome_show` 分离为 `pref_mikubox_welcome_completed`，避免旧版设置使 MikuBox 引导直接跳过。已有安装升级后也会展示一次；点击跳过或开始后保存完成状态，再次启动进入原有启动页及主页。页面重建保存当前页码。

验证：

- JDK 21 下 `:app:lintDebug :app:assembleDebug` 通过，Lint 0 错误、224 警告。
- Android 16 arm64 模拟器独立测试用户、空应用数据，从桌面 launcher alias 启动，显示 MikuBox 引导第一页。
- 依次进入第二页和第三页；第二页横竖屏切换后保持页码，点击开始进入主页。
- 完成后冷启动不重复引导；清除仅测试用户的应用数据后，验证第一页跳过，再启动不重复引导。
- 截图保存在 `app/build/reports/onboarding-2026-09-20/page1.png`、`page2.png`、`page3.png`。

测试使用独立 Android 用户，原用户配置未清除。尚未测试真机。
