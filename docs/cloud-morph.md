# 状态栏流体云胶囊与卡片的连续形变

2026-10-21 R3：仅接管状态栏胶囊点击展开和收起的动画。胶囊直接扩大成卡片，卡片直接缩回胶囊，移除预缩小、留白等待、遮罩退场及回弹的独立阶段。功能跟随模块总开关与流体云外观总开关，系统关闭动画时保持系统行为。锁屏流体云、通知中心、应用跳转、无动画切换及独立 App 界面均不在接管范围内。

## 上一版未覆盖的路径

ColorOS 17 插件的状态栏动画并不都经过普通卡片 `animatorscenes.t / Q`。固件的场景选择器还会按卡片模型和留白能力选择 `y`（`CAPSULE_TO_SMALL_INSTANT_CARD_WITH_BLANK`）和 `J0`（`SMALL_INSTANT_CARD_TO_CAPSULE_WITH_BLANK`）。上一版仅登记了七个具体场景，没有覆盖这两个分支。原测试直接调用已登记场景的回调，也未重放共同构建入口，因此没有发现遗漏。

手机运行报告曾确认上一版动画 Hook 已注册，但没有 `cloud-continuous-morph` 命中。用户反馈状态栏点击展开、收起没有变化。该报告能够证明未实际接管，不能单独确定当时使用的是哪个具体分支。后续按用户要求停止手机确认，本轮不声称已在设备上解决。

此外，`InstantContainer` 的模糊材质实际绑定到 `getInstantView()` 返回的内容根视图。直接裁剪外层容器、并对整个内容根做淡入，会让背景随内容一起淡出。修订后以带材质的实际根视图作为变形表面，仅对其子内容同步可见度。

## 接管与时序

`CloudMorphHook` 在共同构建入口 `animatorscenes.B0.invoke()` 读取 `C0.f()` 返回的原生场景枚举，仅接受以下状态栏路径：

- `CAPSULE_TO_CARD`、`CARD_TO_CAPSULE`、`CARD_TO_CAPSULE_BY_INTERRUPT`。
- `CAPSULE_TO_LARGE_INSTANT_CARD`、`CAPSULE_TO_SMALL_INSTANT_CARD`、`CAPSULE_TO_SMALL_INSTANT_CARD_WITH_BLANK`。
- `LARGE_INSTANT_CARD_TO_CAPSULE`、`SMALL_INSTANT_CARD_TO_CAPSULE`、`SMALL_INSTANT_CARD_TO_CAPSULE_WITH_BLANK`。

该入口返回一个外层 AnimatorScene，第一项是具体形变场景，另一项可能是 `Z` 条件计时器。R3 标记并替换整个外层 Animator，在 `engine.animatorengine.h.g()` 生成原生图后返回一个 300 ms 的 ValueAnimator。视图不在构造 Animator 时确定，而是在实际开始时重新解析；卡片为空、未挂载或尺寸为零时暂停计时，保留胶囊，用主线程 Handler 每 16 ms 重试，最多 32 次。目标就绪后安装初始胶囊轮廓、把播放时间重置到 0 再恢复播放。超时和关闭功能会取消、移除等待任务，并走原生取消监听。

原生 `Z` 计时器不独立播放，也不改写其时长。正常形变结束时检查它的原生 `h` 一次性标记，再调用 `g.invoke()` 推进宿主条件；取消或等待期间不完成该条件，防止首次布局尚未就绪时系统已把过渡推进到下一状态。模块或系统动画关闭时仍保留完整原生路径。

普通卡片使用原生 CardView；普通即时卡片优先使用主卡片宿主中的实际内容根，留白及即时收起路径优先使用 `card.window.i.h.r` 中的实际内容根。主宿主为空、隐藏或尚未挂载时，选择已挂载且可见的另一宿主，避免对占位容器播放动画。胶囊位置和尺寸来自当前 `capsule.i.c` 容器的活动 CapsuleView 及 `getCapsuleDrawableWidth()`。R3 在状态栏 CapsuleContainerRoot 的触摸处理前缓存有效锚点，避免宿主先隐藏或压缩胶囊后读到零尺寸；目标卡片开始时优先刷新有效尺寸，否则复用这次点击前的锚点。

单个进度同步驱动位置、可见宽高、圆角和子内容可见度。主背景从首帧起保持不透明，不播放先缩小再退场的原生属性动画。裁剪复用系统 `common.util.j`，材质继续使用原生 ViewRootManager 和 Drawable。动画轮廓单独标记，避免常规卡片圆角 Hook 在绘制时改写过渡半径。

原 Animator 已有的 start/cancel/end 监听器按原始 Animator 身份转发，维护状态栏通知、窗口和触摸区域生命周期。取消后已移除的监听器不再收到 end。结束后清除旧入场遮罩，恢复内容透明度、材质、背景边界及父级裁剪。300 ms 内的反向操作继承上一帧几何值。视图脱离或关闭功能会取消并清理。

运行报告记录 `cloud-morph-builder`、`cloud-morph-builder-deopt` 和 `cloud-morph-clock` 的注册；`cloud-morph-entry` 表明共同入口选择的场景，`cloud-continuous-morph` 表明已替换计时器，`cloud-morph-started` 表明目标完成布局并实际绘制过首帧，`cloud-morph-await-layout` 表明正在等待首次布局，`cloud-morph-skip` 记录视图缺失、未测量或未挂载等回退原因。注册成功不代表替换已执行。

## R3：首次展开和稳定终态

用户反馈 R2 只有连续打断时略有效。本地检查确认 R2 在首次卡片未完成布局时会放弃接管，而打断时往往已经存在有尺寸的视图；其测试提前完成布局，未覆盖该差异。R3 的冷启动回归从没有内容视图开始，再创建零尺寸视图、完成挂载与布局，验证无需打断也从胶囊尺寸起步。此结论验证代码路径，不代替设备调用证据。

旧实现还会记录零透明度的初始内容，并在每帧乘以进度；背景可能残留原生收起态的 alpha=0 或 1px bounds，外层 InstantContainer 也可能有旧缩放。R3 开始时复位宿主缩放与位移，补齐原生背景 alpha 和完整边界；初始隐藏内容按可见终态揭示。展开结束不能再把开始前保存的压缩 Outline 写回，否则会覆盖原生 end 回调刚恢复的完整卡片。现在安装完整原生平滑 Outline，并恢复完整材质范围。

新增回归覆盖点击前锚点保留（开始动画前胶囊变为零尺寸）、首布局暂停与恢复、条件回调仅在完成时调用一次、零透明度材质和文字、残留外层缩放、压缩 Outline 的终态恢复、等待期间取消及有界超时。按用户要求，R3 未连接或操作手机。

## 本地验证

```sh
python3 scripts/check_effects.py --only CloudMorphCheck
python3 scripts/check_effects.py
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

动画检查通过实际共同入口的合成结构重放：具体场景与外层条件时钟分开处理，覆盖留白展开/收起、延后创建实际卡片视图、首帧背景不透明、无预缩小的单调变形、普通与即时卡片、途中反向、取消回调身份、材质/内容圆角同步，以及锁屏、应用跳转、无动画场景的排除。新增入口检查在修订前因缺少 `B0.invoke` 接管而失败。

主机检查和 Android 构建不验证手机 GPU 帧率、实际窗口裁剪及具体应用模板的视觉效果。按用户要求，本轮仅做本地验证和打包，手机确认留待后续明确安排。

R3 本地验证：161 项动画检查通过，Android 调试构建与 111 项单元测试通过。完整效果回归遇到工作区另一项 `IosRenderPathCheck` 的电池百分比字号断言失败；将流体云实现还原到本轮修改前的版本后同样失败，其余 39 个效果检查与入口路由通过。本轮没有改动电池实现或调整该断言来绕过失败。
