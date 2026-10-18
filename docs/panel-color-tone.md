# 下拉背景颜色深浅

已核对本地 ColorOS 17 SystemUI 反编译源码：
`com.oplusos.systemui.common.util.NotifiAndQsPlatformBlurExKt.panelPlatformMixConfig(Context, boolean, boolean)`
为控制中心和通知中心共用的背景创建 `BlurMixSingleWithShader`，日间使用
`LUMINOSITY(#92000000) + OVERLAY(#66999999)`，夜间使用
`LUMINOSITY(#A6000000) + OVERLAY(#33999999)`。这些是模糊后的颜色处理，
会改变底图颜色，调节模糊半径本身不能去除它们。

“整体背景 → 调整颜色深浅”启用后，复用已有的 `panel-source` Hook，
将上述两层替换为一次普通 MIX；`BlurConfig.getPlatformMixConfig()` 和
`ViewBlurProxy.applyConfigToPlatformBlur()` 等直接消费入口同步应用同一配置，
使已经缓存的背景也能通过现有配置刷新机制更新。

- `-100..-1`：白色混色，减淡背景。
- `0`：去除系统的亮度/灰色叠层；若已启用自定义混色，则保留所选颜色。
- `1..100`：黑色混色，加深背景。
- 关闭调节：恢复保存的自定义混色；自定义混色也关闭时恢复系统原生材质。

绝对值为该黑/白叠色的不透明度百分比。与自定义背景颜色同时开启时，
在配置快照生成时把两次 alpha 混色合成为一层直通 ARGB，避免多次叠色、
每帧计算或丢失用户原先选择的颜色。控制中心和通知中心共用此设置。
保留背景镜像缩放、展开透明度策略与现有连续模糊半径处理；不修改卡片、
通知条或解锁输入框的背景。默认关闭，旧配置迁移后保持原样。

设置使用项目现有的 LiquidToggle、LiquidSlider 薄封装及 MIUIx 文本/分组。
预览使用同一颜色计算。配置格式升级为 v21，仍接受 v3–v20 导入。

验证：`python scripts/check_effects.py --only PanelToneCheck` 运行实际 Hook 回调，
检查中性去叠色、双向端点、生产者/getter/直接后端一致、缓存恢复、异常还原、
缩放和透明度保留、与自定义颜色合成、非面板隔离与总开关。
`PanelToneSettingsTest` 检查保存、导入导出、旧版本迁移及非法数值。
这些检查使用宿主 JVM 替身，不代表真机 GPU 像素或视觉效果已验证。

## 下拉首帧颜色连续性

面板自定义混色的占位色设为透明。原生 `BlendDrawable.onDrawContent` 在异步背景截图尚未到达时直接绘制占位色；完整 ARGB 占位不能代表与截图的混色，会造成首次下拉先显示整层颜色、拿到截图后突然换色。正式截图仍由原生 Shader 按展开进度混色，保留镜像缩放和原生透明度策略。

饱和度接入 `BlendDrawable.onDrawContent` 的共同录制入口，同时处理 Shader Paint 和临时位图 Paint，避免 Shader 未就绪时漏掉饱和度。已有原生滤镜通过外层颜色过滤保留，绘制异常时恢复画笔或 Canvas；数值仍随展开进度连续变化，关闭及复用到非面板材质时使缓存重新录制。

`PanelColorContinuityCheck` 覆盖截图前占位、两条绘制路径和异常恢复；`PanelSaturationCheck` 覆盖渐变与开关恢复。全量测试替身保留同一 `PlatformBlurDrawable.applyBlurConfig` 上的通知卡片与饱和度两个回调，避免后注册者覆盖前者造成错误的隔离结果。这些是宿主 JVM 检查，真机视觉仍需安装并加载新 Hook 后验证。
