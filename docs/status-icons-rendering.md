# 状态栏：C16 图标适配 C17 原生堆叠

本次图形来源为用户提供的 `双排信号状态栏模块C16.zip`。第二包 用户提供的 C17 参考包 只提供 C17 信号资源组织和共享画布的参考。解析 APK 资源时不执行压缩包内的脚本。

## 资源与白名单

`app/src/main/assets/icons/theme-reference` 保存 54 个来源或适配资源；`manifest.json` 记录素材分类、APK 内路径、资源名、原始二进制 XML SHA-256、路径摘要及适配方式。生成器产出 296 项限定映射，其中 130 项为透明角标或透明原图。所有可见路径必须匹配第一包的原始路径，运行时不绘制图标。

普通状态图标、Wi-Fi、横向电池和蓝牙电池来自 C16。第一包没有的高性能、流量节省、竖向电池、独立电池装饰及 ADB 素材、映射已删除，相关系统资源正常回退。设置页也移除高性能和流量节省的样式示例。图标种类缺口不会用第二包或旧手绘图补足。保留的别名只适配同一图标在 C17 中的不同资源名称。

C16 普通图标和蓝牙电池的外部 inset 被移除，路径与内部变换保留。普通图标、Wi-Fi 与信号固有宽高放大 12%，画布两侧各扩展 1dp 并等量移动路径，避免改变图形比例；相邻图标因此增加约 2dp 间距。横向电池在导入时按原图 26 × 15.376dp 等比放大 12%，整体约 29.12 × 17.22dp；保留原始 93 × 55 画布及上下留白，可见主体高约 14.09dp。生成器不再重复放大电池。蓝牙动画仅在所有帧有来源时替换，帧时序沿用系统；animated-vector 保留系统实现。

## 双卡与单卡信号

C16 的 `stat_signal_signal_lte_single_0..4` 本身画了两排，上排是递增的圆角柱，下排是圆角短条。原包下排在部分状态下固定点亮一格或两格，不能直接复制到 C17 的每张卡上。

导入器原样提取两排路径，生成 C17 的 `stat_signal_lte_signal_stacked_primary_0..4` 与 `secondary_0..4`，共用 19 × 16dp 画布。原 19 × 13 的两排图形垂直居中，上下排各自只包含四个路径；副卡透明度按自己的档位设置，未点亮格保留 C16 的 `0x35` 透明度。主、副卡的信号档位、排列与堆叠选择仍由系统决定，支持 25 种组合。soft、无语音与旧资源名复用相同图形。

单卡 C17 资源只保留上排并垂直居中，避免出现固定的假副卡。堆叠无服务从 C16 的灰色短条提取；独立无服务使用第一包的单行灰色资源。SOS 保留系统图标。

## 透明角标与系统状态

按用户补充要求继续保留透明替换：Wi-Fi / 移动信号角标、代数、双 Wi-Fi 标记、上下行箭头、HD / VoLTE / VoWiFi 与漫游标记隐藏。透明资源保持宿主所需类型，不受可见图标种类清理影响。

C17 的 Wi-Fi 4 / 5 / 6 / 6E / 7 可以由 `wifi_left_tv` 文字控件或 `wifi_left` 图片显示，透明资源不能隐藏文字。iOS 样式在 `OplusWifiSignalExImpl.bindEx` 初次绑定后，以及其 `$bindEx$2$1$2$1.invokeSuspend` 状态更新后，清空并隐藏这两个角标控件，保留 `wifi_signal` 主图。

移动网络文字继续在 `Os17Binding.bindNetworkTypeText` 的输入置为 null，并在绑定后清理文字和图片。另在其 `$bindSubclassFlows$1$1.emit` 完成后清理 `mobileTypeText`、`mobileTypeImage`、`mobileRoamingText`，覆盖优化后的绑定路径和重复状态更新；`bindRoamingText` 的显示输入仍为 false。仅操作宿主的这些角标视图，不修改网络状态、信号强度或 StateFlow。系统默认样式保留原显示。

## 电池与窗口

C16 横向电池的画布为 93 × 55，主体范围 x=0..85、y=5..50。C17 的 `BatteryBarDrawableBase.setDrawableBounds` 会把背景和进度都放到同一个矩形中；若把进度画布裁为主体大小，系统会将其重新拉伸，导致进度比背景大。现在背景与进度共同保留原始 93 × 55 画布及路径位置，宽高统一按原图放大 12%，整体约 29.12 × 17.22dp，可见主体约 26.62 × 14.09dp。保持水平方向与垂直方向相同的缩放系数，不裁剪上下留白，也不把原图压成 28 × 17dp 的高胖主体。百分比外置及无 padding 分支复用同一画布。旧 ProgressBar 入口的四个进度边距设为 0，避免再次扣除已包含在图形中的留白。保留 `LayerDrawable → ClipDrawable` 和 `android:id/progress`，系统继续处理电量、颜色、充电、百分比与 RTL。

`getBodyRectForLayout(RectF)` 与 `getOffsetX()` 的现有百分比对齐适配到 85/93 主体比例，排除电池头，系统继续测量并居中一位、两位、三位数字。蓝牙电池使用第一包的耳机及电量图形。竖向电池保持系统资源。

状态栏仍通过 `SystemBarUtils.getStatusBarHeight` 和 `getStatusBarHeightForRotation` 在系统结果上增加 4dp，同步窗口高度与应用避让，保留挖孔、旋转、嵌套调用与异常恢复处理。电池前原生 separator 继续额外增加 1dp。

## 加载与验证

`Resources.getDrawableForDensity`、`TypedArray.getDrawableForDensity` 按完整资源名提供同类型原生 drawable；Compose 通过 `Resources.getXml` 读取独立矢量。模块引用由模块 Resources 解析。未知资源、类型不符或加载失败回退系统，不接管 Canvas。样式、尺寸、文字隐藏与高度在 SystemUI 启动时固定，修改后需重启系统界面。运行报告标记 `signals=status-c16-c17-stacked;battery=status-c16`，待重启时附加 `restart-required=true`。

重新导入和生成：

```sh
python3 scripts/import_theme_icons.py /path/to/双排信号状态栏模块C16.zip /path/to/C17.zip
python3 scripts/generate_status_drawables.py
```

验证资源、来源与实际 APK：

```sh
python3 scripts/check_status_drawables.py --c16 /path/to/双排信号状态栏模块C16.zip --apk /path/to/app-release-unsigned.apk
python3 scripts/check_effects.py --only IosStatusBarCheck IosDrawSafetyCheck IosRenderPathCheck IosCoverageCheck
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

资源检查验证每条可见路径确实来自第一包、来源哈希、过期图标删除、透明角标、原生类型、别名引用、25 种双卡组合、电池同画布进度及打包尺寸。Hook 检查覆盖 XML / Compose / TypedArray 加载、默认样式、密度、尺寸、Wi-Fi 数字与网络文字的初次绑定和重复更新隐藏、状态栏高度、异常回退、RTL 与百分比居中。

`scripts/render_theme_icons.py` 生成的 [预览图](status-icons-preview.png) 是实际资源的离线渲染，不是真机截图。设备上仍需检查浅深色、双卡强弱变化、SIM 无服务、充电和状态栏避让。
