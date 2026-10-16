# 状态栏：直接使用 PUI ColorOS 17 原始资源

当前实现只从用户提供的 `PUI Theme For ColorOS 17_v17.0.0.137.zip` 取得图标。第一包 `pui.zip`、描摹轮廓、曲线拟合和项目自行绘制的替代图标均不再参与当前样式。主题包的安装、挂载脚本不执行。

## 资源与尺寸

`app/src/main/assets/icons/pui-reference` 保存 69 个原始资源及来源清单，包括状态图标、Wi-Fi / 蜂窝信号、横向与竖向电池、蓝牙电池及 ADB 状态资源。保留包内的路径、画布、分组变换、不透明度和图标比例，只适配系统 tint、模块引用、原生类型和进度 ID。Wi-Fi 的 inset 留白在需要 VectorDrawable 的入口折入画布，蓝牙 inset 的原始子矢量在需要 VectorDrawable 的入口内联；没有重新绘制路径。

生成器提供 301 个限定资源映射，包括原图别名和透明角标资源。包内没有的热点、投屏、旋转锁定等状态保持系统图标。原生蓝牙动画沿用帧时序与包内帧图；不兼容的 animated-vector 保持系统实现。主题包中不属于状态栏的装饰和快捷设置图标不接入此功能。

普通图标、Wi-Fi 与蜂窝信号的最终 drawable 固有宽高增加 12%，路径与主副卡的相对位置保持原样；最终画布左右各增加 1dp 透明留白，viewport 按同一物理比例扩展，并整体平移原图，因此相邻图标增加约 2dp 间距，图形不会被拉宽或缩小。C17 的信号 ImageView 使用 wrap_content，普通系统图标使用 CENTER，仅修改 status_bar_* 的尺寸配置无法使它们放大，因此必须同步调整原图的固有宽高。以 14dp 状态图标为例，图形宽高变为 15.68dp，含留白的资源宽度为 17.68dp；堆叠信号的图形为 21.28 × 17.92dp，含留白的宽度为 23.28dp。图标容器和引用尺寸资源的固定尺寸入口仍增加 12%，为原图留出空间；横向电池高度保持 17dp，宽度从 34dp 收窄为 28dp，减少文字前后多余空间；横向进度间距与文字横向偏移按 28/29 同步缩放，保持边框、进度和文字对齐。纵向进度间距、内部文字、纵向文字偏移及圆角恢复 17dp 高度对应的 C17 基准比例。电池前的原生 separator 额外增加 1dp，配合前一图标右侧 1dp 留白补齐约 2dp 的间距。原包的负数蓝牙电池 inset 保留方向和比例。外部百分比沿用系统字号。

状态栏通过 `SystemBarUtils.getStatusBarHeight` 和 `getStatusBarHeightForRotation` 在原结果上增加 4dp。C17 的根布局、窗口各旋转参数与 `providedInsets`（statusBars / tappableElement）共同使用这两个结果，因此窗口高度与应用避让区域同步改变。系统先处理挖孔、安全区域和旋转，模块再增加空间。嵌套高度方法只在最外层增加一次，并在失败后清理调用深度。修改仅作用于模块的 SystemUI 进程。

## 角标与系统状态

Wi-Fi / 移动信号的角标、代数、双 Wi-Fi 标记、上下行箭头、HD/VoLTE/VoWiFi 与漫游资源设为透明；复合信号直接采用无附加标记的原包信号资源，无服务使用原包灰色零格信号。SOS 未替换，仍由系统提供。

C17 的小 5G 是 `OplusStatusBarMobileViewBinder$Os17Binding.bindNetworkTypeText` 绑定的文字。iOS 样式把其显示输入置为 null，让系统自己的分支清空文字和厂商图片；`bindRoamingText` 的显示输入置为 false。状态更新后继续保持隐藏，不修改网络状态模型、信号强度或 StateFlow。

电池保留 `LayerDrawable → ClipDrawable` 与 `android:id/progress`。系统继续处理电量 level、充电、颜色、百分比与 RTL；包内原始图形作为外观。双卡是否堆叠与主副卡排列仍由系统选择。

## 加载与重启

`Resources.getDrawableForDensity`、`TypedArray.getDrawableForDensity` 按完整资源名替换且保持具体 drawable 类型；Compose 通过 `Resources.getXml` 取得无需模块引用的原生矢量。包含引用的图层和动画由模块 Resources 加载。未知资源、类型不符或加载失败回退系统图标，不接管 Canvas 或电池绘制。仅在 C17 横向电池的 `getBodyRectForLayout(RectF)` 返回后，将文字布局区域对准原图主体（原画布 2200，主体右边界约 1979），排除电池头并适配 RTL。原生代码继续在每次电量或字体更新后测量数字的实际轮廓、进行像素取整和居中，兼容 `7`、`75`、`100`；`getOffsetX()` 的大字体回退也使用相同主体中心。默认样式保留系统区域与偏移。

样式、尺寸、文字隐藏与高度在 SystemUI 启动时固定。修改样式、切回默认或修改总开关后需重启系统界面。报告包含 `signals=pui-c17;battery=pui-c17;icons=112%;height=+4dp;gap=+2dp;battery-width=28dp;battery-height=17dp;battery-text=body-centered;network-text=hidden`；配置与进程不一致时附加 `restart-required=true`。

## 验证

运行 `python scripts/check_status_drawables.py --apk app/build/outputs/apk/release/app-release-unsigned.apk`、`python scripts/check_effects.py`，以及 Gradle 的 `:app:testReleaseUnitTest :app:lintRelease :app:assembleRelease`。资源检查确认每个可见图形完全来自原包 XML，覆盖引用、类型、信号各档、透明角标与电池进度；另外核对放大后的固有宽高及打包 APK 中的实际宽高，防止仅修改尺寸配置却未放大图片。电池原始 XML 不再追加放大，避免重复缩放；运行检查覆盖默认样式、密度、小数及负数尺寸、5G 文字绑定、嵌套高度与异常恢复、28dp 电池宽度与 17dp 高度、内部文字、一至三位数字的像素居中、RTL、大字体回退及两种 separator 入口。

`status-icons-pui.png` 是实际打包资源的离线预览，不是真机截图。设备上仍需检查窗口 / 应用避让、横竖屏、挖孔、主题、双卡与充电等实际状态。
