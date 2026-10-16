# 状态栏图标来源与参考

- 实心百分比电池参考：[9to5Mac，2022-08-08，iOS 16 battery percentage](https://9to5mac.com/2022/08/08/ios-16-battery-percentage/)。参考图中的电池为完整实心圆角块，数字反白居中；不采用描边加内嵌进度块。
- [该报道实机图](https://9to5mac.com/wp-content/uploads/sites/6/2022/08/iphone-battery-percentage-1.jpg)。仅研究比例，没有打包进应用。
- [Apple 官方百分比说明](https://support.apple.com/zh-cn/guide/iphone/iph4747a74a9/ios)。
- 主参考：[Apple 官方 iPhone 使用手册：了解 iPhone 状态图标的含义](https://support.apple.com/zh-cn/guide/iphone/iphef7bb57dc/ios)。Wi-Fi、单卡 / 双卡信号、闹钟、勿扰、热点、方向锁定、耳机、飞行模式、定位、VPN 依据该页图片描摹。
- 参考图及 SHA-256：见 [status-icons-reference.json](status-icons-reference.json)。原图是 Apple 的说明插图，不属于 MIT 图库，也不代表 Apple 提供了可直接用于 Android 的图标包。
- `scripts/trace_apple_status_icons.py` 提取参考比例；`scripts/refine_status_icons.py` 使用对称曲线、统一线宽和规则圆角重新构建小尺寸轮廓，避免将低分辨率参考图的边缘噪声带入矢量。信号柱统一柱宽、圆角与基线。`scripts/render_status_icons.py` 从这些 SVG 生成 Java 路径。应用预览与 XML 资源生成使用同一份可编辑 SVG，最终尺寸由资源和系统容器决定。
- 蓝牙、静音、振动、NFC、高性能采用项目绘制的配套图形，不声称它们是当前 iOS 官方状态栏图标。静音与振动单独映射，保留不同状态含义。充电闪电参考官方充电电池插图后用规则折线重绘。旧 Ionicons 图形已从此功能移除。

当前 SystemUI 接入只替换编译后的 drawable XML；电池本体与百分比由系统计算和绘制。电池 SVG 和早期设计图仅作比例研究，不代表当前功能。应用内预览已移除电池展示。时间字体、双卡排列和容器布局跟随系统。

`scripts/generate_status_drawables.py` 将现有 SVG 与资源名映射生成 298 个静态 Android 资源，由系统选择状态并完成绘制。信号各档、漫游、异常、无服务、网络制式和主副卡分别提供资源。蓝牙动画使用原生 animation-list；与系统具体类型不一致的资源保留原生实现。资源类型清单见 `status-icons-resource-inventory.json`，详细接入及重启要求见 [资源方案](status-icons-rendering.md)。

网络标签与警告字母在生成阶段转换为 Work Sans Bold 的静态轮廓，不依赖运行时字体绘制。该字体使用 SIL OFL 1.1，许可随应用分发于 `app/src/main/assets/licenses/status-labels.txt`；不分发 Apple 字体。`scripts/fixtures/iconstyle/label-glyphs.json` 保存生成所需的字形轮廓。

图形轮廓继续使用现有连续圆角与对称贝塞尔曲线设计。Android XML 资源采用原生 VectorDrawable，由系统负责 tint、alpha、状态更新和实际绘制。修改图标样式后需重启系统界面，关闭或开启均不会在当前进程重绘切换。

Canvas 校对见 [status-icons-canvas.png](status-icons-canvas.png)，设计原则见 [status-icons-canvas-philosophy.md](status-icons-canvas-philosophy.md)。`scripts/draw_status_icons_canvas.py` 用 Skia Canvas 直接绘制同一份路径与光学尺寸，保留 1 px/dp、2.75 px/dp、3 px/dp 状态栏及小图形对照。另在 1、2、2.75、3 倍密度下检查图形、描边与状态标记的栅格越界，并用 Skia 曲线边界独立核对生成器的极值计算。此画布使用 canvas-design 的 Instrument Sans / DM Mono；真机数字使用 Android 系统字体，画布不能验证实际 SystemUI 的字体和布局。

预览是设计示意，不是真机截图，也不代表所有字体与系统图形已达到像素级复刻。设备上仍需验证不同 DPI、主题、刘海空间及更多状态组合的最终像素效果。
