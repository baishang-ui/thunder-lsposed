# 流体云胶囊与展开卡片的连续形变

2026-10-20：胶囊直接扩展为卡片，收起沿相同路径缩回胶囊。功能跟随模块总开关与流体云外观总开关，使用系统现有视图和材质，未修改独立 App 设置页。

本地 ColorOS 17 `SystemUIPlugin.apk` 中，普通展开场景 `animatorscenes.t` 同时创建胶囊退场和卡片入场场景；卡片入场 `card.animation.H` 还包含先放大到 1.03、前景遮罩淡出、内容偏移和后续回弹。收起 `animatorscenes.Q` 另行创建卡片退场和胶囊入场。两套属性动画造成了胶囊与卡片分段切换的观感。

`CloudMorphHook` 在原生场景构建后标记其根场景，并在 `engine.animatorengine.h.g` 生成 Android Animator 后替换为一个 300 ms 的 ValueAnimator。胶囊位置来自当前 `capsule.i.c` 的容器及其活动 CapsuleView，宽度读取 `getCapsuleDrawableWidth()`；卡片终点使用完成布局后的实际位置和尺寸。普通卡片、打断后的收起，以及大/小即时卡片共用这条路径。

单个进度同步驱动位置、可见宽高、圆角和内容可见度。主背景全程保持不透明，移除原生遮罩与放大回弹阶段。内容与材质裁剪复用系统 `common.util.j`，背景继续使用原生 `ViewRootManager` 和 Drawable。动画轮廓单独标记，避免常规卡片圆角 Hook 在每次绘制时把过渡半径改成展开终值。

原始 Animator 不播放属性动画，但它已有的 start/cancel/end 监听器仍按原始 Animator 身份转发，维护事件状态、状态栏通知、窗口释放和触摸区域生命周期。取消时先转发 cancel，已自行移除的原生监听器不会再收到 end。结束后恢复裁剪、前景、内容透明度、背景边界与父级裁剪；多个卡片共享父级时以引用计数恢复。300 ms 内的反向操作继承上一帧的几何值。关闭功能或视图脱离时取消并清理；系统关闭动画或卡片尚未测量时保留原生路径。

验证命令：

```sh
python3 scripts/check_effects.py --only CloudMorphCheck
python3 scripts/check_effects.py
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

合成检查覆盖起终点尺寸、位置和圆角，内容/材质边界同步，单调增长且无过冲，反向衔接，原生回调身份与取消语义，多种卡片入口，关闭开关、脱离窗口及测量失败的回退。主机检查和 Android 构建不验证手机 GPU 帧率、实际窗口边界及应用模板的视觉效果；仍需在 ColorOS 17 真机检查点击展开、点击外部收起、快速反向与锁屏切换。
