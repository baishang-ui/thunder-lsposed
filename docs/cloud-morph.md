# 状态栏流体云连续形变：早期版本记录

本文保留 R3–R5 的排查与早期实现记录。当前统一表面的动画、原生交互、入口衔接和验证结果见 [流体云动画与原生交互修复](cloud-motion-repair-2026-10-21.md)，其中也记录了尚未完成的真机视觉验证。

2026-10-21，早期可调版：仅处理状态栏胶囊与卡片之间的视觉动画。目标是从当前胶囊直接扩大成卡片，收起时直接缩回胶囊，去掉预缩小、留白、遮罩和第二次入场。锁屏、通知中心、应用跳转、无动画场景及独立 App 设置页不在接管范围内。

## R3 回归与架构修正

用户反馈 R3 点击后连卡片都不展开。旧实现替换了整个原生 Animator，在目标卡片有尺寸之前暂停播放，且此时不转发原生 start。宿主若在 start 回调中才挂载或显示卡片，就会互相等待。原替身测试预先提供目标视图，因此没有发现这层依赖。R4 曾采用目标未就绪时完整退回原生的兜底；用户明确要求继续实现所有状态栏路径的直接形变，因此当前 R5 采用下面的架构。

**原生 Animator、start/cancel/end、挂载流程和条件计时器完整保留。** `CloudMorphHook` 不再替换 `h.g()` 的返回值、不暂停宿主动画，也不手动调用原生监听器或 `Z.g.invoke()`。宿主先正常执行 start 并创建实际卡片，模块再驱动单独的视觉进度；目标晚于 start 创建时，通过原生窗口的 pre-draw 和有界的主线程重试接入，绝不取消宿主来处理布局超时。

## 入口与视觉控制

共同入口 `animatorscenes.B0.invoke()` 通过原生枚举 `C0.f()` 限定以下状态栏场景，标记外层图；`engine.animatorengine.h.g()` 成功生成原生 Animator 后只追加一个观察监听器：

- `CAPSULE_TO_CARD`、`CARD_TO_CAPSULE`、`CARD_TO_CAPSULE_BY_INTERRUPT`。
- `CAPSULE_TO_LARGE_INSTANT_CARD`、`CAPSULE_TO_SMALL_INSTANT_CARD`、`CAPSULE_TO_SMALL_INSTANT_CARD_WITH_BLANK`。
- `LARGE_INSTANT_CARD_TO_CAPSULE`、`SMALL_INSTANT_CARD_TO_CAPSULE`、`SMALL_INSTANT_CARD_TO_CAPSULE_WITH_BLANK`。

点击前在 `CapsuleContainerRoot.onTouchEvent()` 缓存活动胶囊的尺寸与屏幕位置。目标卡片未准备好时，绘制前保持这个完整胶囊，覆盖原生预缩小的缩放和 Drawable 宽度；原生挂载继续进行。卡片就绪后，300 ms 的独立 ValueAnimator 同步驱动位置、可见宽高、圆角和内容可见度。

每个原生窗口在 pre-draw 阶段重新应用当前形变状态，覆盖同一帧中原生动画写回的缩放、位移、alpha、裁剪、过渡 blur、前景遮罩和临时 GONE 状态。视觉控制在所有属性动画更新之后、实际绘制之前生效，因此原生的胶囊退场/卡片入场分段不会作为第二段视觉效果显示。

普通卡片使用原生 CardView。即时卡片使用 `getInstantView()` 的真实材质根视图，按主宿主和 `card.window.i.h.r` 的实际挂载状态选择。轮廓复用系统 `common.util.j`，背景继续使用 ViewRootManager 和原生 Drawable；不创建替代卡片或自绘控件。进入前恢复完整材质 bounds 与 alpha，避免复用卡片残留 1px 背景或透明内容；终态使用完整的原生平滑轮廓。

视觉进度到达终点后，pre-draw 会继续保持完整的卡片/胶囊终态，直到宿主原生动画结束，避免原生剩余阶段再写入一轮收缩或遮罩。收起时宿主若提前隐藏或卸载窗口，则暂缓这两个窗口操作，保留卡片直到视觉时钟结束；正常状态回调不重放。结束与取消移除观察器、重试任务和视觉时钟，释放父级裁剪；最终 GONE/VISIBLE 仍尊重原生结束回调，不能复活已经移除的旧宿主。途中反向使用最后实际渲染的几何值。

## 报告

- `cloud-morph-entry`：当前状态栏场景。
- `cloud-morph-native-lifecycle`：已向原生 Animator 追加视觉观察，未替换它。
- `cloud-morph-started` / `cloud-continuous-morph`：目标就绪并开始实际形变。
- `cloud-morph-skip`：目标始终不可用时只退出视觉观察，宿主动画继续运行。

注册或追加观察不等于设备已实际播放新形变。

## 本地验证与限制

```sh
python3 scripts/check_effects.py --only CloudMorphCheck
python3 scripts/check_effects.py
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

新的回归先验证 Hook 返回的确实仍是同一个原生 Animator，然后由原生 onAnimationStart 创建首次卡片，检查它第一次就进入直接形变。每个模拟帧在视觉时钟更新后重新写入原生缩放、透明度、压缩 Outline、GONE、blur 与遮罩，再触发 pre-draw，检查旧分段无法覆盖连续几何。另覆盖晚挂载时保持完整胶囊、双向单调变化、原生回调只执行一次、原生结束前保持视觉终态、打断反向、状态栏外场景排除、关闭清理与缺失目标的正常原生完成。

这些检查验证参数、绘制前写回和生命周期，不验证手机 GPU 外观与帧率。用户随后授权手机调试；已核对当前插件、原生字段语义和新 PID 的运行报告，末端用短录屏验证。完整审查见 [动画链路审查](cloud-animation-chain-audit.md)。

## 动画选项

流体云样式中新增“优化展开与收起”开关。开启后可调整时长（120–1000 ms）、曲线（iOS 式缓出 / 平滑缓动 / 轻快缓出 / 匀速）与回弹（0–100%）。默认 300ms、iOS 式缓出、0 回弹，保留原生按压反馈。独立 App 与系统设置入口使用同一份配置，下一次动画读取新参数；关闭优化即恢复原生动画。配置格式 v24，兼容导入 v3–v23。

收起末段逐步交回真实胶囊内容，避免几何已经成为胶囊时仍空白、随后突然出现图标文字。关闭窗口的原生请求只在当前收起尚未完成时延迟；新展开打断旧收起时不再执行旧的隐藏请求。
