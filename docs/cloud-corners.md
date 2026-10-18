# 流体云圆角的跨层处理

本次核对基于本地采集的 ColorOS 17 `SystemUIPlugin.apk`、`SeedlingSdk.apk`。只修改宿主 Hook，没有新增模块 UI 或自行绘制卡片。

## 根因与边界

卡片不是单个背景。旧实现虽然处理了 `CardBackgroundView`、模糊 Drawable、渐变和一种外层 Outline，但内容模板、独立 SDK 和按压效果仍保存各自的圆角。外层命中 `cloud-card` 不代表这些内层已更新。

| 层 | 固件入口 | 处理 |
| --- | --- | --- |
| 外层材质、渐变、填色 | `BackgroundUtils.b`、`seedling.util.c.f`、`seedling.util.d` | 延续同一像素半径；背景重建前交还原生裁剪，重建后再应用 |
| 卡片及动画裁剪 | `common.util.k.c` → `common.util.j.getOutline` | 保存原生半径和动画缩放；实际裁剪半径为 `设置 dp × density / scale`，保留动画 Rect 和平滑权重 |
| 按压高光 | `seedling.card.ui.view.k.b` | 绘制时更新构造时缓存的 Triple，绘制后或异常后恢复 |
| 音乐模板裁剪、描边 | `MediaBackGroundView`、`CustomLottieView.i`、`component.view.c.getOutline` | 仅流体云卡片祖先内的背景视图采用卡片半径；不覆盖封面、按钮及普通通知模板 |
| 模板光效 | `component.light.f.draw` → `g.d[6..9]` | 同步四角，在原生遮罩强度 100% 时也生效；恢复 CPU 参数后标记 uniform 重新上传 |
| SDK 内容、描边、COE 背景 | `g7.f2.s0` → `c0` / `h1` / `e1` | 使用 SDK 原生 `f8.j5`，半径单位保持 dp；实时设置重放现有消费者 |
| SDK 新光效后端 | `t7.e0.K0` → `c0` / `g0` | 单独更新其缓存样式、两个裁剪宿主及已存在的光效参数，不能只修改 `g7.f2.s0` |

SDK 只匹配已有展开组件：StatusBar、LockScreen、SecondaryLockScreen 使用流体云卡片圆角；Notification 使用通知卡片圆角，独立于流体云外观总开关。其他入口保留自身样式，胶囊继续使用独立的胶囊参数。模板按当前父级归属判断，不能仅凭共享模板类名套用卡片圆角。

通知中心和锁屏通知列表的流体云还存在另一条边界：外层通知背景与 SDK / 音乐模板描边并非同一层。此前 Notification 入口被排除，导致外层通知圆角已经变化而内层白色描边仍保持原生半径。现在 SDK 的裁剪、描边和两种光效后端一起跟随通知圆角；音乐模板在 `ExpandableNotificationRow` 祖先内读取该行实际背景配置，移出通知后重新按当前归属判断。锁屏 CUSTOM 材质只替换模糊、混色，保留该通知或横幅的圆角，避免锁屏样式意外清除通知圆角。独立展开的流体云不采用通知圆角。

`common.util.k.c` 会新建 Triple，丢失来源对象上的附加信息，同时除以动画 scale。现在把未覆盖的原生基线和 scale 保存到具体 OutlineProvider。关闭自定义后仍能恢复原生动画半径，不会从已经覆盖的数值推算恢复值。模板 `strategy.f.H` 读取资源期间使用可嵌套作用域，防止原生基线被资源替换污染。

所有临时绘制参数在 after Hook 中恢复，包括异常路径。配置刷新会使已发现的模板及 Outline 重新绘制；SDK 在关闭自定义时恢复一次，后续配置刷新不重复重建未修改的原生形状。类型和平滑权重继续使用原生值。

## 验证

`python scripts/check_effects.py --only CloudGeometryCheck CloudCheck CloudLayerCheck CloudLockCheck CloudSdkCheck CloudTemplateCheck` 覆盖动画 scale、原生基线恢复、0/12/32/64 dp 连续切换、四角 uniform、SDK 两种后端、描边、关闭总开关、通知隔离、嵌套资源读取及绘制异常。新增测试在修复前因缺少动画裁剪边界而失败。

合成运行检查验证参数和生命周期，不代表 GPU 外观、所有应用模板或进出动画已通过真机视觉验收。真机报告应分别核对 `cloud-template-corners`、`cloud-sdk-corners`、`cloud-press-corners`，并观察同一卡片连续调参后的背景、内容、描边和按压边界。

`CloudNotificationGeometryCheck` 补充通知入口、流体云总开关关闭、通知 0/64/12/22 dp 实时切换、模板描边/光效、异常恢复、脱离通知祖先、锁屏材质与通知圆角合并、独立锁屏卡片隔离和关闭自定义后的恢复。修复前首项因 SDK 仍返回原生 20dp 而失败。需在真机通知中心与锁屏中观察同一卡片的白色描边，确认固件实际渲染结果。
