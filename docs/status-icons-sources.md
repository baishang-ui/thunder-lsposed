# 状态栏图标来源与参考

- 实心百分比电池参考：[9to5Mac，2022-08-08，iOS 16 battery percentage](https://9to5mac.com/2022/08/08/ios-16-battery-percentage/)。参考图中的电池为完整实心圆角块，数字反白居中；不采用描边加内嵌进度块。
- [该报道实机图](https://9to5mac.com/wp-content/uploads/sites/6/2022/08/iphone-battery-percentage-1.jpg)。仅研究比例，没有打包进应用。
- [Apple 官方百分比说明](https://support.apple.com/zh-cn/guide/iphone/iph4747a74a9/ios)。
- 主参考：[Apple 官方 iPhone 使用手册：了解 iPhone 状态图标的含义](https://support.apple.com/zh-cn/guide/iphone/iphef7bb57dc/ios)。Wi-Fi、单卡 / 双卡信号、闹钟、勿扰、热点、方向锁定、耳机、飞行模式、定位、VPN 依据该页图片描摹。
- 参考图及 SHA-256：见 [status-icons-reference.json](status-icons-reference.json)。原图是 Apple 的说明插图，不属于 MIT 图库，也不代表 Apple 提供了可直接用于 Android 的图标包。
- `scripts/trace_apple_status_icons.py` 提取参考比例；`scripts/refine_status_icons.py` 使用对称曲线、统一线宽和规则圆角重新构建小尺寸轮廓，避免将低分辨率参考图的边缘噪声带入矢量。信号柱统一柱宽、圆角与基线。`scripts/render_status_icons.py` 从这些 SVG 生成 Java 路径。应用预览和 SystemUI 使用同一份路径，无第二次独立缩放。
- 蓝牙、静音、振动、NFC、高性能采用项目绘制的配套图形，不声称它们是当前 iOS 官方状态栏图标。静音与振动单独映射，保留不同状态含义。充电闪电参考官方充电电池插图后用规则折线重绘。旧 Ionicons 图形已从此功能移除。

电池绘制为项目自己的 Canvas 矢量实现，电量变化只更新内部数字，保留完整实心本体；低电量、低电量模式与充电配色来自系统。数字使用 Android sans-serif bold 并按实际字形边界居中，不分发 Apple 字体。

iOS 风格下，时间最大 17 dp，普通图标容器 20 dp，横向电池容器 30 × 16 dp。时钟在 C17 `Clock.onMeasure` 之前更新字体并清除 `mSecondsFixedWidth`，根据实际文本、秒数预留宽度和父布局的宽高限制适配字号，避免长时间或上午 / 下午文本被截断。系统原始尺寸、字体、图片缩放和外侧百分比可实时恢复。双卡选项通过 C17 的 isStackableMode 状态流切换原生布局，不写入系统偏好；保留系统的双卡可用性判断和每张卡的信号状态。单卡不会创建第二张卡。

接入依据为本地 ColorOS 17 SystemUI 固件：独立信号既使用 `stat_signal_signal_lte_single_*_os17` / `stat_signal_soft_signal_*_os17` 资源，也使用直接绘制的 `SignalDrawable`；堆叠信号由 `OplusStackedMobileIconLayoutStrategy.SignalIcon-cf5BqRc` 加载两个 Drawable 并在 Compose Box 中叠放。替换仅在该堆叠组件作用域内包装 AppCompatResources 返回的普通信号资源，沿用原生 DrawablePainter 的颜色和生命周期；两层图形共享画布，上下分行。无服务、漫游和异常状态继续使用原生提示。

Wi-Fi 原生 ImageView 使用 FIT_END，替换时改为 FIT_CENTER，并把 FrameLayout 的纵向布局改为居中。电池使用 Drawable 完整边界，不再套入厂商内部进度矩形；充电标记放在本体内，充电前后本体尺寸一致。

预览是设计示意，不是真机截图，也不代表字体与所有系统图形已达到像素级复刻。设备上尚待验证不同 DPI、系统主题与状态栏空间的适配。
