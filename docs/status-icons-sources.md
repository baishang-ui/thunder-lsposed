# 状态栏图标来源与参考

- 实心百分比电池参考：[9to5Mac，2022-08-08，iOS 16 battery percentage](https://9to5mac.com/2022/08/08/ios-16-battery-percentage/)。参考图中的电池为完整实心圆角块，数字反白居中；不采用描边加内嵌进度块。
- [该报道实机图](https://9to5mac.com/wp-content/uploads/sites/6/2022/08/iphone-battery-percentage-1.jpg)。仅研究比例，没有打包进应用。
- [Apple 官方百分比说明](https://support.apple.com/zh-cn/guide/iphone/iph4747a74a9/ios)。
- 主参考：[Apple 官方 iPhone 使用手册：了解 iPhone 状态图标的含义](https://support.apple.com/zh-cn/guide/iphone/iphef7bb57dc/ios)。Wi-Fi、单卡 / 双卡信号、闹钟、勿扰、热点、方向锁定、耳机、飞行模式、定位、VPN 依据该页图片描摹。
- 参考图及 SHA-256：见 [status-icons-reference.json](status-icons-reference.json)。原图是 Apple 的说明插图，不属于 MIT 图库，也不代表 Apple 提供了可直接用于 Android 的图标包。
- `scripts/trace_apple_status_icons.py` 提取参考比例；`scripts/refine_status_icons.py` 使用对称曲线、统一线宽和规则圆角重新构建小尺寸轮廓，避免将低分辨率参考图的边缘噪声带入矢量。信号柱统一柱宽、圆角与基线。`scripts/render_status_icons.py` 从这些 SVG 生成 Java 路径。应用预览和 SystemUI 使用同一份路径，无第二次独立缩放。
- 蓝牙、静音、振动、NFC、高性能采用项目绘制的配套图形，不声称它们是当前 iOS 官方状态栏图标。静音与振动单独映射，保留不同状态含义。充电闪电参考官方充电电池插图后用规则折线重绘。旧 Ionicons 图形已从此功能移除。

电池绘制为项目自己的 Canvas 矢量实现，电量变化只更新内部数字，保留完整实心本体；低电量、低电量模式与充电配色来自系统。数字使用 Android sans-serif bold 并按实际字形边界居中，不分发 Apple 字体。

iOS 风格下，时间最大 17 dp。时钟在 C17 `Clock.onMeasure` 之前更新字体并清除 `mSecondsFixedWidth`，根据实际文本、秒数预留宽度和父布局的宽高限制适配字号，避免长时间或上午 / 下午文本被截断。重复测量会缓存适配结果，避免字号来回变化。

图标使用固定的设计坐标与分组缩放比例：普通图形 0.8、Wi-Fi 0.9、信号 18/22、数据活动箭头 0.62。不再将各个图形强行缩放到同一最大高度；窄图形保留原有比例。容器高度 20 dp，宽度按真实贝塞尔曲线极值加左右各 1.5 dp 计算，连续主图形目标可见间距为 3 dp；警告、漫游、无语音与连接状态不改变基础图形的尺寸或位置。移动信号单独调整光学中心，使柱底与 Wi-Fi、电池底边接近。电池占位 30 × 20 dp，本体高度 13 dp，数字周围保留内边距，充电闪电适当加大。

图形与控件使用相同宽高比例，避免 ImageView 先按原图比例缩小一遍。替换期间清除相关叶子与容器的额外 padding / margin，固定宽度的中间容器随内容测量；原生溢出点、出现动画和显隐逻辑仍由系统处理。

接入依据为本地 ColorOS 17 SystemUI 固件。已核对的 298 个资源名收录在 `scripts/fixtures/iconstyle/status-resources.txt`：包含普通状态、漫游、无服务、无语音、Wi-Fi 异常、Wi-Fi 6 / 6E / 7、网络制式、VoLTE / VoWiFi、数据活动及反向充电。Android 专属状态使用项目配套图形，不表示它们是苹果原生状态栏图标。异常、漫游和连接状态通过斜线、字母或标记保留含义。普通系统图标接入 `StatusBarIconView.getIcon`、资源 setter 和直接 Drawable 绑定；XML 在挂接到状态栏之前设置的 src 会在状态栏测量时补替换。通知中的应用标识不在替换范围。

独立信号既使用 `stat_signal_signal_lte_single_*_os17` / `stat_signal_soft_signal_*_os17` 资源，也使用直接绘制的 `SignalDrawable`。后者保留信号强度、无服务、网络异常、运营商切换状态及原生颜色、透明度。`StatusBarIconView` 的图标绘制缩放与测量缩放仅在替换期间临时归一化，调用结束（包括异常）后恢复字段，保留原生出现动画和溢出点。

堆叠信号由 `OplusStackedMobileIconLayoutStrategy.SignalIcon-cf5BqRc` 加载两个 Drawable 并在 Compose Box 中叠放；两层共享坐标，上下分行，分别保留真实强度与无服务状态。双卡选项通过 C17 的 isStackableMode 状态流切换原生布局，不写入系统偏好；保留双卡可用性判断，单卡不会创建第二张卡。

Wi-Fi 原生 ImageView 使用 FIT_END，替换图标改为 FIT_CENTER 并纵向居中。原本叠放的信号、制式与数据活动图形重新排列为独立的相邻单元；原生 TextView 网络标记统一字号并居中，重复的 spacer 清零。只剩一个可见单元时同样重算起点，消除隐藏图标留下的旧间距。关闭替换时恢复容器尺寸、边距、字体和对齐。

电池在原生 binder 中复制不可变的 BatteryIconStyle UI 模型，把横向、竖向、圆形及旧版横向统一交给原生横向绘制器创建，再用本项目路径绘制；隐藏样式继续隐藏。保留主题、颜色、密度与最新电量更新，使用完整 Drawable 边界，不套入厂商内部进度矩形。闪电在本体内显示，外部重复的充电标记与百分比暂时隐藏；反向充电单独保留标识。切换关闭时用保存的最新原始模型重新绑定，恢复系统样式，不修改系统设置。

`python3 scripts/check_effects.py --only IosCoverageCheck IosStatusBarCheck IosRenderPathCheck` 验证资源覆盖、图形范围、间隔、直接绑定、原生二次缩放、组合图标不重叠、布局重复测量稳定、样式恢复及时间完整显示。SVG 设计预览采用与 Java 相同的路径范围和尺寸算法；应用内预览直接调用实际 Drawable。

Canvas 校对见 [status-icons-canvas.png](status-icons-canvas.png)，设计原则见 [status-icons-canvas-philosophy.md](status-icons-canvas-philosophy.md)。`scripts/draw_status_icons_canvas.py` 用 Skia Canvas 直接绘制同一份路径与光学尺寸，保留 1 px/dp、2.75 px/dp、3 px/dp 状态栏及小图形对照。另在 1、2、2.75、3 倍密度下检查图形、描边与状态标记的栅格越界，并用 Skia 曲线边界独立核对生成器的极值计算。此画布使用 canvas-design 的 Instrument Sans / DM Mono；真机数字使用 Android 系统字体，画布不能验证实际 SystemUI 的字体和布局。

预览是设计示意，不是真机截图，也不代表所有字体与系统图形已达到像素级复刻。设备上仍需验证不同 DPI、主题、刘海空间及更多状态组合的最终像素效果。
