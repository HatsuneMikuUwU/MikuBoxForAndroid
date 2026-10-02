# 启动重叠与闪现修复

## 复现

Android 14 arm64 模拟器，开启“显示启动画面”和底部模糊。旧版逐帧录屏中，启动图、名称和页脚与主页列表同时可见。这不是主界面加载失败，而是整个 splash 根布局的 alpha 动画把两个内容页叠在了一起。

此外，窗口获得焦点、提交首帧与 Android 系统 splash 退出不是同一事件。只依赖焦点或 pre-draw，可能在系统窗口移交期间提前启动动画。

## 修改

- 同一条 400 ms 时间线：前 140 ms 隐去启动图和文字，保持背景不透明；后 260 ms 只淡出背景遮罩，显露主界面。主界面出现时 splash 的图文已完全不可见。
- Android 12+ 的启动路由明确传递系统 splash 移交标记。等待系统退出回调、首帧提交和窗口焦点就绪，再执行动画。
- 首次引导页完成后属于应用内跳转，不等待不存在的系统 splash 退出回调；恢复 Activity 时不重复添加启动图。
- View 脱离窗口时清理焦点监听和动画，避免残留回调。

系统启动页路由与退出回调参考 [Android 官方迁移文档](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate)。

## 验证

证据目录：`app/build/reports/splash-2026-09-20/`。最终包冷启动录屏按 30 ms 采样检查：2.27–2.33 s 为完整启动图，随后启动图在纯色背景上隐去；2.48 s 起显露主页，此时启动图文字已消失。未出现旧版的两页交叉重影，也未再发生移交期间系统背景闪回。时间是该段录屏的时间轴，不是跨设备启动耗时承诺。

- `:app:lintDebug :app:assembleDebug` 成功；Lint 0 errors / 232 warnings。
- 返回前台时系统直接带回现有 MainActivity，不重新播放启动图。
- 关闭“显示启动画面”后冷启动直接进入主页，未出现自定义启动图；补测后恢复原设置。
- 冷启动动画完成后主页可操作，crash buffer 为空。
- 本轮验证环境为 Android 14 arm64 模拟器；没有据此宣称所有实体手机或 Android 7–11 的帧率表现。

## APK

`app/build/outputs/delivery/MikuBox-20260920-splash-arm64-v8a.apk`

SHA-256：`a2cb1c3f2b4ca63fa9b238b6fc8a2509c83e69c06cb8b24b1aea564175d48782`。

apksigner 校验通过，沿用上一版测试包签名。
