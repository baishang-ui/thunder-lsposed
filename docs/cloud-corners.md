# 流体云圆角的跨层处理

本次核对基于本地采集的 ColorOS 17 `SystemUI.apk`、`SystemUIPlugin.apk`、`SeedlingSdk.apk`。形状通过宿主原生 Hook 调整，设置页沿用现有库组件。

## 根因与边界

卡片不是单个背景。旧实现虽然处理了 `CardBackgroundView`、模糊 Drawable、渐变和一种外层 Outline，但内容模板、独立 SDK 和按压效果仍保存各自的圆角。外层命中 `cloud-card` 不代表这些内层已更新。

| 层 | 固件入口 | 处理 |
| --- | --- | --- |
| 外层材质、渐变、填色 | `BackgroundUtils.b`、`seedling.util.c.f`、`seedling.util.d` | 延续同一像素半径；背景重建前交还原生裁剪，重建后再应用 |
| 卡片及动画裁剪 | `common.util.k.c` → `common.util.j.getOutline` | 保存原生半径和动画缩放；实际裁剪半径为 `设置 dp × density / scale`，保留动画 Rect 和平滑权重 |
| 按压高光 | `seedling.card.ui.view.k.b` | 绘制时更新构造时缓存的 Triple，绘制后或异常后恢复 |
| 音乐模板裁剪、描边 | `MediaBackGroundView`、`CustomLottieView.i`、`component.view.c.getOutline` | 仅流体云卡片祖先内的背景视图采用卡片半径；不覆盖封面、按钮及普通通知模板 |
| 普通模板根容器 | `page.entity.t.k`、`page.entity.s.a` → `common.util.j.getOutline` | 精确登记模板根 ViewGroup；同步内容裁剪与原生前景描边，保留动画缩放，替换描边或关闭自定义时恢复原值 |
| 模板光效 | `component.light.f.draw` → `g.d[6..9]` | 同步四角，在原生遮罩强度 100% 时也生效；恢复 CPU 参数后标记 uniform 重新上传 |
| SDK 内容、描边、COE 背景 | `g7.f2.s0` → `c0` / `h1` / `e1` | 使用 SDK 原生 `f8.j5`，半径单位保持 dp；实时设置重放现有消费者 |
| SDK 新光效后端 | `t7.e0.K0` → `c0` / `g0` | 单独更新其缓存样式、两个裁剪宿主及已存在的光效参数，不能只修改 `g7.f2.s0` |

SDK 只匹配已有展开组件：StatusBar、LockScreen、SecondaryLockScreen、Notification 统一使用流体云卡片圆角，并受流体云外观总开关控制。其他入口保留自身样式，状态栏胶囊继续使用独立的胶囊参数。模板按当前父级归属判断，不能仅凭共享模板类名修改未归属的背景。

通知中心和锁屏中的流体云均在“流体云 → 展开卡片”调整圆角，普通通知继续使用通知圆角。`CUSTOM` 外层材质按当前场景选择通知、横幅或锁屏模糊与混色，但形状始终读取流体云卡片参数；关闭流体云或卡片圆角后恢复系统形状，不回退到通知自定义圆角。SDK 的内容裁剪、描边和两种光效后端、音乐模板描边和四角光效使用同一流体云半径。堆叠背景按所属视图选择同一参数，代理重用于普通通知时清除流体云标记。

锁屏 `CapsuleItemView.getActualRadius()` 另行驱动 `AutoRoundRectPathProvider` 的内容裁剪及 `CapsuleBackgroundHelper.setStrokeRadius()` 描边，不能只修改 `BlurConfig`。流体云数据类型 `CapsuleSeedingData` 的实际半径与最大半径统一采用卡片参数，并按当前裁剪高度的一半限制。普通通知胶囊不受影响。实时刷新调用原生 `applyRoundnessAndInvalidate()` 重建缓存路径与描边，关闭自定义时由原生重新计算。

用户反馈边缘仍有一层不同步后，进一步从同一份 C17 固件确认了两条未覆盖的边缘路径：

- 通知 `NotificationBackgroundView.setRadius(float,float,boolean)` 同时驱动背景层、focus overlay，以及 `NotificationBackgroundViewExtImp.updateRadius()`。后者直接调用 `BaseDrawable.setCornerParams()` 并刷新阴影，绕过 `ViewBlurProxy.applyBlurConfig()`。在 `setRadius` 接收处按当前所属流体云和原生上下圆角动画比例改参，避免依赖可能已被 ART 内联的上游半径 getter；反优化调用者 `ActivatableNotificationView.applyRoundnessAndInvalidate()`。普通通知按自身通知圆角配置处理，与流体云参数独立。
- 锁屏 `stroke.StrokeView` 是独立动画边缘视图，`updateViewSize()` 会把每个 `StrokeLayer` 的半径直接重设为当前高度的一半。它不读取 `CapsuleItemView.getActualRadius()`。在该视图绘制期间同步每层原生 shader 半径，按这层自己的当前高度限制，绘制完成或异常后恢复缓存；配置变化会使该独立视图重绘。仅 `CapsuleSeedingData` 所属视图生效。

模板 shader 的形状分支也已核对，但本次没有据此统一改写原生形状类型或平滑权重；本次追加修复针对上述已确认绕过旧入口的路径。

`common.util.k.c` 会新建 Triple，丢失来源对象上的附加信息，同时除以动画 scale。现在把未覆盖的原生基线和 scale 保存到具体 OutlineProvider。关闭自定义后仍能恢复原生动画半径，不会从已经覆盖的数值推算恢复值。模板 `strategy.f.H` 读取资源期间使用可嵌套作用域，防止原生基线被资源替换污染。

所有临时绘制参数在 after Hook 中恢复，包括异常路径。配置刷新会使已发现的模板及 Outline 重新绘制；SDK 在关闭自定义时恢复一次，后续配置刷新不重复重建未修改的原生形状。类型和平滑权重继续使用原生值。

## 验证

`python scripts/check_effects.py --only CloudGeometryCheck CloudCheck CloudLayerCheck CloudLockCheck CloudSdkCheck CloudTemplateCheck` 覆盖动画 scale、原生基线恢复、0/12/32/64 dp 连续切换、四角 uniform、SDK 两种后端、描边、关闭总开关、通知隔离、嵌套资源读取及绘制异常。新增测试在修复前因缺少动画裁剪边界而失败。

合成运行检查验证参数和生命周期，不代表 GPU 外观、所有应用模板或进出动画已通过真机视觉验收。真机报告应分别核对 `cloud-template-corners`、`cloud-sdk-corners`、`cloud-press-corners`，并观察同一卡片连续调参后的背景、内容、描边和按压边界。

`CloudNotificationGeometryCheck` 覆盖流体云总开关、0/64/12/22 dp 实时切换、通知参数隔离、普通通知保留、模板描边/光效、异常恢复、锁屏材质与流体云圆角合并、锁屏原生裁剪和描边源、尺寸限制及关闭恢复。修复前因 Notification 入口仍返回通知半径失败；锁屏边缘专项在缺少 `getActualRadius` Hook 时失败。合成检查不代替手机视觉验收。

追加回归用固件的尺寸重设行为和直接描边更新路径重放，覆盖 0/12/64 dp、分组圆角动画、阴影与 focus overlay、收起高度限制、异常恢复、普通通知复用和关闭恢复。分别移除两处新增修复后，检查因“尺寸重设后独立边缘未使用流体云半径”和“focus overlay / 分组圆角未同步”失败，日志为 `.local/cloud-edge-lock-behavior-red.log`、`.local/cloud-edge-shade-behavior-red.log`；当前实现通过 82 项流体云几何检查。这些主机检查只验证调用参数和生命周期，手机最终边缘外观仍待用户验证。

## 展开与锁屏反馈后的追加修复

继续按同一份 C17 固件的调用顺序核对，确认两处旧检查未覆盖的入口：

- 展开卡片的 `SingleCardPage` 将模板根容器保存在 `page.entity.t.k`。这是普通 `ViewGroup`，此前 `CloudHook.cardSurface()` 不会识别。`page.entity.s.a()` 还会从 `strategy.f.H()` 安装内容 Outline，并独立替换 XML 前景描边。现在在页面构造和模板观察者回调处登记该容器，内容轮廓继续保留原生 Rect、平滑参数和逆动画缩放，前景只更新圆角。页面先构造后挂载时，在首次 Outline 计算处补齐同步；同值不会反复改写 Drawable。更换前景时恢复旧对象，关闭自定义或移出流体云宿主后恢复原生逐角数组。
- 锁屏 `CapsuleItemView.onClipInfoChange()` 会绕过 `getActualRadius()`，直接把 `CapsuleConfig.getDefaultRadius()` 写入 `AutoRoundRectPathProvider`。现在在该原生方法成功结束后，用 provider 自带的 `updateMetaData(radius, null, null)` 重建圆角缓存，保留它刚计算的裁剪区域、缩放和平滑模式。半径仍限制在当前裁剪高度的一半；普通胶囊和关闭自定义时保持原生行为。

此外，模板的归属检查补上 `CapsuleSeedingData` 锁屏宿主，使锁屏流体云中的模板裁剪、描边和光效也使用卡片参数。

测试先重放上述写回再检查最终消费者，旧实现分别因“普通模板根裁剪仍使用默认值”“锁屏裁剪变化重置内容圆角”失败。首次挂载与锁屏模板归属也各自验证过修复前失败。完整 `scripts/check_effects.py` 通过，其中跨层几何 60 项、通知与锁屏流体云几何 96 项。日志保存在 `.local/cloud-template-root-red.log`、`.local/cloud-clip-change-red.log`、`.local/cloud-template-attach-red.log`、`.local/cloud-lock-template-red.log` 和 `.local/cloud-followup-all-effects.log`。这些检查验证 Hook 参数与生命周期；此次按用户指示不做真机核验。
