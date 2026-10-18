# 系统背景混色算法核对

范围包括整体面板、控制中心卡片、通知条、通知横幅、流体云胶囊与展开卡片，以及模糊、静态、普通填色和软件绘制回退路径。依据是本地采集的 ColorOS 17 SystemUI.apk、SystemUIPlugin.apk、oplus-framework.jar 和 framework.jar。代码与数学回归使用合成的 Android/Oplus 替身，不等于真机 GPU 验证。

## 统一的颜色含义

界面中的颜色与透明度定义为普通 alpha 混色。在不透明底图上，每个颜色通道满足：

```text
a = 所选 ARGB 的 A / 255
最终颜色 = 实际背景 × (1 − a) + 所选 RGB × a
```

平台 Shader 按固件通道数值插值，不额外转换为线性光。ARGB 作为未预乘的 RGBA 上传，RGB 不再乘一次 alpha。

| 颜色 | 灰色背景 `#808080` 上的结果 |
| --- | --- |
| `#FF000000` | `#000000` |
| `#FFFFFFFF` | `#FFFFFF` |
| `#80000000` | 约 `#404040` |
| `#80FFFFFF` | 约 `#C0C0C0` |
| `#00000000` | `#808080` |

`#80` 为 128/255，约 50.2%。0% 清除当前路径的背景叠色，保留模糊；关闭自定义则恢复系统配置。100% 在完整显示的背景区域输出所选纯色。视图淡入淡出、描边、阴影、按压态和通知堆叠装饰仍由系统控制。

同一颜色在不同半透明卡片上可能呈现不同结果，因为背景取样、模糊半径、面板下方叠层与位置不同。统一算法保证颜色和不透明度的含义一致，不保证不同底图输出相同像素。

## 原先造成差异的路径

1. 共用卡片改成了 MIX，但面板仍使用原生亮度混色及第二层颜色，横幅仍继承原生模式。
2. 平台 Shader 的单层配置会在 `ShaderBlendParamHelper.updateMixSingleShaderParams()` 中把颜色 alpha 乘以展开量，多层前景颜色不做这一步；随后 Drawable 在 `alphaWithBlurAmount=true` 时还会设置自己的 alpha，造成部分卡片在动画中重复衰减。面板开启背景缩小时原生使用 `false`，使背景在起始 5% 后保持不透明，仅让 Shader 颜色随展开量变化；强制改为 `true` 会让缩小背景与底下原尺寸背景叠显。
3. 上一版把动态模糊改成 `BackgroundBlurDrawable.setColor()`，这个判断不完整。框架中的 `draw()` 先绘制该 Paint，再绘制 RenderNode；`setAlpha()` 更新合成器参数，不会同步乘到这支 Paint 的颜色上，因此不能当作材质混色的等价替代。
4. 流体云使用原生材质模式 4，且资源替换会同时把所选颜色写入 blend、mix 和无模糊背景参数，存在多次叠色。纯色回退还会向多个填色子层分别写入相同 ARGB。
5. 无模糊快捷卡片保留了原生颜色滤镜；单层与多层 Shader 的软件绘制占位颜色也不一致。
6. 面板生产者会把替换配置保存在 BlurConfig 中，关闭自定义必须追溯到原生对象，不能只停止替换 getter。

## 两套原生模式编号

AGSL 定义来自 `ShaderBlendParam.Companion.getModeString()` 与 `BlurDrawableShaderBlendStringKt`：

| AGSL 模式 | 名称 |
| --- | --- |
| 0 | NONE |
| 1 | MIX |
| 2 | OVERLAY |
| 3 | DODGE_BG |
| 4 | DODGE |
| 5 | LUMINOSITY |

固件分支原文：

```glsl
if (blendMode == 1) { col.xyz = mix(col.xyz, blendColor.xyz, blendColor.w); }
if (blendMode == 4) { col = colorDodgeBlend(blendColor, col); }
```

模式 4 不能表达界面所示的不透明度。例如完全不透明的黑色作用于灰色仍保留灰色。

动态模糊与流体云调用的 `OplusBlurParam.setMaterialParams()` 使用另一套枚举：

| OplusBlurParam 模式 | 常量 |
| --- | --- |
| 0 | BLUR_BLEND_MODE_DEFAULT |
| 1 | BLUR_BLEND_MODE_COLORMIX |
| 2 | BLUR_BLEND_MODE_OVERLAY |
| 3 | BLUR_BLEND_MODE_COLORDODGE |
| 4 | BLUR_BLEND_MODE_GLOW_OVERLAY |
| 5 | BLUR_BLEND_MODE_GLOW_COLORDODGE |

该接口第一个数组为 blendColor，第二个为 mixColor。自定义背景现在选择原生 COLORMIX，清空 blendColor，只向 mixColor 写入一次所选 RGBA。两套接口恰好都用编号 1 表达所需混色，不能据此把其他编号互换。已核对框架常量、参数存储及 Java 调用链；未取得底层合成器源码，原生 COLORMIX 的逐像素实现与颜色空间仍待真机验证。

## 各路径的处理

| 路径 | 自定义混色处理 |
| --- | --- |
| 面板、卡片和横幅平台 Shader | 保留原生单层/多层类型与镜像几何；统一 MIX，清空其余颜色层 |
| Shader 动画 | 卡片、横幅自定义配置通过 Drawable alpha 跟随展开量，跳过 helper 的额外 alpha 相乘；整体面板保留原生 `alphaWithBlurAmount` 策略，为 `false` 时保留 helper 的颜色动画，使缩小的背景保持原生不透明度，避免原尺寸背景透出；保留原生 full-opacity 场景控制 |
| Shader 软件绘制回退 | 单层和多层均设置相同的所选占位填色；原生 Drawable 将其 alpha 与绘制 alpha 相乘，作为硬件绘制的替代路径 |
| 动态模糊 | 原生 COLORMIX + 单一 mixColor，额外 Paint 遮罩为 0；原配置为空也创建所需材质 |
| 静态模糊 | `getBlurColor()` → `setBlurMaskColor()`，使用一层所选颜色遮罩 |
| 低高斯面板壁纸 | 暂停原生固定强度 overlay，绘制一次所选 ARGB × currentAlpha，恢复原生标记；自定义不依赖原标记是否开启 |
| 无模糊快捷卡片 | 临时写入所选填色 × Drawable alpha，清除原生显式颜色滤镜；绘制结束或异常后恢复颜色、滤镜及 callback |
| 无模糊通知背景 | 原生 `NotificationBackgroundView.setTint()` 的 GradientDrawable 分支直接设置 ARGB，随原生背景动画绘制 |
| 流体云原生模糊 | 按背景链中实际安装的管理器 Drawable 判断后端，使用 COLORMIX；清空额外普通填色和渐变 |
| 流体云普通填色 | 只给最后一个可见填色子层写入所选颜色，清空其他填色；支持嵌套 LayerDrawable、DrawableWrapper、GradientDrawable 和 ColorDrawable，保留无填色描边 |
| 流体云资源 | 背景、渐变及材质颜色不再由资源入口覆盖；由运行时背景钩子统一处理，保证原生重建和关闭恢复读到原始资源 |
| 自动文字对比度 | 使用同一 alpha 合成公式；横幅估算不再叠加可能未显示的面板颜色。壁纸主色和前台应用背景之间的差异仍是估算限制 |

面板生产者替换保留原生来源，关闭自定义后 getter 与直接消费字段的后端都会读取原生配置。临时字段在异常后恢复。原生未标记的 Shader 保留其动画。快捷开关的开启、不可用及不可操作状态、滑条进度色、锁屏解锁输入框等不属于共用背景覆盖范围。

背景缩小由 C17 的 `applyPanelMirrorScale()` 每帧通过 `getPlatformMixConfig().setMirrorScale()` 写入，开启时从 1 插值到 0.94。开启混色后该 getter 返回替换对象，因此必须将替换对象的缩放写入同步到保留的原生来源，否则下一次 getter 或后端重建会用来源中的旧值覆盖动画。`panel-mirror-scale` 仅对带原生来源的替换对象同步；同时对原生动画 helper 及 Scrim 调用入口去优化，避免编译内联绕过钩子。回归覆盖展开、收起、连续 getter、直接后端、换色和关闭混色后的缩放保留。

缩放同步与背景透明度分别处理：`panelMix()` 每次创建或复用配置都保留来源的 `alphaWithBlurAmount`，不会把所选混色透明度用于淡化整张截图。单层 Shader 仅在 Drawable 已经按展开量淡入时跳过颜色 alpha 相乘。回归同时检查原生 `true`/`false` 两种策略、0%/半透明/100% 混色及下拉各阶段的背景不透明度，防止缩小恢复后出现双背景叠影。

C17 的 `PlatformBlurDrawable.applyBlurConfig()` 在单层 Shader 且 `alphaWithBlurAmount=false` 时，会在 `platformBlurRadiusForAmount()` 返回后将非零进度的半径抬至至少 15px；`panelClosedOnDown=true` 时下限为 25px。保留原生背景不透明度后，这一下限会覆盖自定义的连续半径，导致起始几帧突然变糊，甚至使自定义 0 半径失效。`panel-continuous-radius` 仅在启用整体面板自定义模糊的 `ViewBlurProxy.applyConfigToPlatformBlur()` 调用内，约束最终 `BlurParam.setBlurRadius()` 写入值为所选半径 × 展开进度；嵌套的其他平台代理暂停该作用域，正常结束或异常后恢复。仅调整混色、关闭自定义模糊或关闭模块时继续使用系统半径策略。该修正不改变背景透明度、颜色插值和缩放。

## 验证与限制

`CardBlendCheck` 运行实际钩子，覆盖四种区域、单层/多层配置，黑白和彩色、0/半透明/全不透明值，黑灰白底图，以及 0、0.25、0.5、1 的展开进度。检查包括额外层清空、软件占位色、动态原配置为空、静态与动态遮罩、异常还原、关闭自定义及生产者缓存还原。数学计算复用了固件 AGSL 分支与 helper/Drawable 的 alpha 处理规则，未执行原生渲染器。

其他效果检查覆盖流体云非空管理器却走纯色回退、嵌套多填色只着色一次、背景资源不重复注入、无模糊颜色滤镜的恢复、壁纸 overlay、横幅角色切换和文字估算。新增回归在修正前分别暴露动画标记、滤镜、占位色等缺陷。

`PanelStartFrameCheck` 在实际模块回调之后模拟上述固件半径下限，核对最终写入参数；覆盖 15/25px 两条分支、0/1/73/200 自定义半径、展开与收起的起始小进度、混色开关、嵌套非面板代理、异常作用域还原及系统恢复。此检查在修正前于 0.1% 展开进度复现了 0 半径被抬高至 15px 的失败。

运行 `python3 scripts/check_effects.py`，也可通过 `--only PanelStartFrameCheck CardBlendCheck HookCheck QsTintCheck QsStateCheck CloudCheck CloudLayerCheck NotificationStyleCheck AdaptiveTextCheck` 单独检查混色路径。Android 单元测试、Lint 与 Release 构建另行执行。最终硬件合成、广色域/HDR、真实底图与动画视觉表现仍需设备检查。
