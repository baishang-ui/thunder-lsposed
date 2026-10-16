# 状态栏图标：仅替换 drawable 资源

模块准备静态 Android XML drawable，SystemUI 继续决定当前资源、电量、信号状态、颜色、布局、动画与绘制。`IosIconDrawable` 仅用于应用和设计预览，不进入 SystemUI 绘制链。

## 实现

- `IosStatusResources` 是完整资源名到模块 drawable ID 的静态白名单，目前准备 298 个资源。资源位于 `app/src/main/res/drawable/thunder_ios_*.xml`，通过 `scripts/generate_status_drawables.py` 从现有图形和状态映射生成。
- 在 `Resources.getDrawableForDensity` 与 `TypedArray.getDrawableForDensity` 返回原生 drawable 后，只替换匹配白名单、包名为 `com.android.systemui` 且具体类型相同的资源。模块通过仅加载资源的包 Context 取得 Android 原生 VectorDrawable / AnimationDrawable / InsetDrawable，不创建自定义 Drawable 包装。
- Compose 的 `vectorResource` 直接读取 XML，因此另在 `Resources.getXml` 提供编译后的静态矢量 XML，由系统继续解析和绘制。该入口仅处理已核对的 vector，文件内无主题、字体或跨包资源引用；动画列表等含模块资源 ID 的文件不交给宿主 XML 解析器。
- 系统选择哪个信号强度、主副卡、漫游、无服务、Wi-Fi 异常或网络制式资源，就取得对应静态图形。不读取 SignalDrawable 编码，不读取或复制电池模型，不接管 `onDraw`、`dispatchDraw`、`VectorDrawable.draw`，不修改 binder、状态流或布局。
- 蓝牙动画列表仍使用原生动画资源，保留系统 500 毫秒帧时序。无法保持原生具体类型的资源继续使用系统实现，例如原生 AnimatedVectorDrawable。
- 未列入白名单、非 SystemUI 包、没有可用模块资源、资源加载异常等情况保留系统资源。单个资源加载失败后隔离该资源，记录 `status-bar-ios-resource-fallback`，不反复加载。通知中的应用图标不在白名单内；共用同一状态资源的锁屏、下拉界面可能同步改变。

## 电池与系统直接绘制的信号

已核对 C17 的 `StatBatteryMeterView` 使用专门的 BatteryContentDrawable 与原生电量模型。其电池本体、百分比、充电配色与进度继续由系统计算和绘制。反向充电的静态资源可替换。直接创建的 SignalDrawable 也保留系统实现。

本次不再承诺把动态电池变成预览中的实心百分比电池。应用预览已移除这种电池展示，避免与实际功能不符。仓库中的旧电池 SVG 和设计图仅为图形研究，不能说明设备已替换。

## 生效与重启

图标样式在 SystemUI 进程安装 Hook 时从完整配置快照读取一次，此后固定。新配置会保存，但资源缓存、Compose 缓存与已有 drawable 仍使用当前进程的样式。切换到 iOS 风格、切回系统默认，以及影响已加载样式的模块总开关变更，都需要重启系统界面。

设置页提供持续提示，可在配置同步后使用右上角“重启作用域”。不会因为选择样式而自动终止系统界面。首次安装、升级或需要卸载旧绘制 Hook 时，仍应正常重启手机。

运行报告的 `style` 表示当前进程实际加载的样式；`renderer=resources;signals=system;battery=system` 表示资源方案。选择与实际样式不一致时附带 `restart-required=true`。资源加载命中分别为 `status-bar-ios-resource-drawable` 与 `status-bar-ios-resource-xml`。

## 验证边界

```sh
python3 scripts/check_status_drawables.py
python3 scripts/check_effects.py --only IosStatusBarCheck IosDrawSafetyCheck IosRenderPathCheck IosCoverageCheck
python3 scripts/check_effects.py
./gradlew :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

资源检查验证白名单与资源文件完整性、XML 无跨包引用、各级信号状态及动画时序。合成 Android / Oplus 检查验证包名与类型保护、资源和 Compose XML 入口、异常回退、关闭与开启均需重启、以及不存在状态栏绘制和状态 Hook。Android 构建验证实际资源能编译和打包。

`docs/status-icons-resource-inventory.json` 记录已采集固件的资源种类及可读取的尺寸；2 个旧映射资源在采集包中没有找到，仍准备兼容资源。部分尺寸依赖固件 dimen，生成文件使用设计默认值。固件并非全部采用相同加载路径，298 个已准备资源不等于 298 个真机命中。不同 DPI、深浅色、双卡和动画的实际显示及长期稳定性仍需 ColorOS 17 真机验证。
