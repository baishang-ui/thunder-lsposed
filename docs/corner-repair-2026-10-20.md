# 2026-10-20 圆角修复

## 设备证据

PLQ110 / `PLQ110_17.0.0.104(CN01)` 的现有模块运行报告在 `getTopCornerRadius`、`getBottomCornerRadius`、`setRadius`、`applyBlurConfig` 和 `applyConfigToPlatformBlur` 中持续记录 `Missing method: ExpandableNotificationRow.getTopRoundness/getBottomRoundness`。

从同一设备读取的 SystemUI 中，这些 Roundable 转发方法带 `bridge/synthetic` 标记。旧 `Reflect` 无条件排除 bridge，查找失败后整段 before/after Hook 被保护模式中止。修复改为优先查找普通实现，缺少普通实现时允许桥接方法，并补齐继承自接口的默认方法查找；仍拒绝真正有歧义的重载。设备独立进程运行实际 `Reflect`，成功解析三个原生桥接方法，并在真实通知行与 RoundableState 上读取 `top=0.5 / bottom=0.25`。该探针没有向正在运行的 SystemUI 注入新版模块。

## 修改范围

- 通知、横幅和通知中心流体云：修正上述共用反射入口；背景和 blur 后端统一沿父级查找所属通知行，避免多层容器下对已经修改的像素半径再次缩放。保留分组通知的上下圆角动画比例及各场景的配置归属。
- 控制中心音乐磁贴：补齐媒体主背景、主题背景和过渡背景登记，覆盖挂载和背景重建。独立 MultiLight 的四角、平滑权重和形状分支跟随背景几何；圆形/胶囊采用普通圆角分支，其余采用 G2。绘制完成或异常后恢复原生 uniform 并标记重新上传。按压路径补齐缺失的原生 OplusPathAdapter，配置刷新会重建现有媒体轮廓和光效缓存。
- 流体云展开卡片及其共享背景：无模糊分支复用插件 `common.util.j` 原生平滑轮廓，替换原先的普通 `setRoundRect` 裁剪，保留宿主曲线类型、权重、裁剪 Rect、动画缩放及原生恢复基线。
- 锁屏流体云：`applyRoundnessAndInvalidate` 重建后同步内容路径与 `bgView.setStrokeRadius`。即使原生半径 getter 被内联，描边也不再保留旧值；保留宿主平滑模式和高度限制。
- 系统设置主页入口：分类标题由不换行空格改为空字符串，去除额外空标题行。继续使用宿主 COUI 的分类、入口布局及间距。

## 验证边界

`python3 scripts/check_effects.py` 执行实际 Java Hook 源码，包含通知、横幅、堆叠背景、流体云展开卡片、模板、独立 SDK、锁屏和音乐磁贴的回归。新增桥接用例在 JVM class 文件中保留转发方法实现、设置与设备一致的 bridge/synthetic 标记；旧反射实现明确失败。媒体登记、嵌套通知比例、锁屏内容/描边同步和流体云无模糊平滑轮廓用例均先在修复前失败。

构建验证使用 `:app:testReleaseUnitTest :app:lintRelease :app:assembleRelease`。合成回归与独立原生探针不能替代新版模块注入后的完整 GPU、进出动画及界面视觉验收。修复包已在连接设备上覆盖安装，保留模块数据。随后在启用流体云卡片圆角时，设备暴露出下述同步回调递归问题；此前通过的参数回归没有覆盖这一行为，不能据此认定设备稳定。

## 流体云启用后的崩溃

设备 `/data/tombstones/tombstone_21` 与 `tombstone_22`（2026-10-20 14:42）记录 SystemUI 主线程栈溢出，反复出现 `View.rebuildOutline → GradientDrawable.setCornerRadius → CloudTemplateHook.PageSurface.apply → getOutline`。末端的 LSPosed JNI abort 是递归耗尽栈后的结果。用本次构建的 R8 mapping 将 `mj.a` 和 `j2.beforeHookedMethod` 还原后，定位到既有的模板前景描边更新：`applied` 在 setter 返回后才更新，但原生 setter 会同步使轮廓失效，重入时又开始相同更新。关闭和替换前景时的 restore 也有同类风险。

`PageSurface` 现在对单个前景的 apply、restore、replacement 使用可异常退出的重入保护。重入的轮廓仍读取当前选择的圆角，但不会再次修改前景。`CloudTemplateReentryCheck` 模拟 Android 同步失效链，覆盖首次挂载、0/12/64/13 dp 修改、关闭恢复、替换前景、原生八角数组恢复、setter 失败重试和关闭模块。旧实现的首次挂载用例明确失败。

收到安全模式反馈后，已撤回本次临时打开的普通通知圆角，并暂时关闭流体云卡片圆角，保留用户选择的圆角数值。不会通过改写 LSPosed 数据库跳过框架的安全模式。


重入修复后的验证：完整效果回归通过（包括 11 项同步重入用例），111 项 release 单元测试通过，release lint 0 错误（59 条现有警告），release APK 构建、v3 签名及 16 KB 对齐验证通过。设备独立探针直接使用本次构建产物中的 `CloudTemplateHook$PageSurface`，配合真实 Android `GradientDrawable` setter 和同步 Drawable.Callback，执行七次圆角设置与原生恢复，回调深度均为 1。该探针仍不等同于 SystemUI 全场景视觉验收。

包含重入修复的 APK 已覆盖安装；恢复阶段曾临时关闭流体云圆角，之后检测到用户主动重新启用并调整数值。后续曲线修复保留用户当前选择。LSPosed 安全模式和作用域数据库未改写。


## 通知中心与锁屏曲线差异的后续修正

用户进一步反馈等半径时背景与描边仍有差异。固件核对确认：插件和 SDK 的 G2 曲线常用权重 3，通知和锁屏裁剪存在 CONIC / 默认平滑权重约 1.1，锁屏普通描边又有 G2 权重 2，独立 StrokeLayer 初始权重为 1。只同步半径而保留各层原生类型/权重会留下曲线差异。

自定义流体云卡片现在统一使用原生 G2、权重 3：包括通知中心行裁剪、主背景及堆叠背景、直接描边写入、锁屏 AutoRoundRectPathProvider、两种锁屏平台描边分支、StrokeLayer、插件轮廓/按压、模板普通前景/音乐描边/光效，以及 SDK 两种后端。只修改流体云所属视图；普通通知继续使用自己的配置。保留上下圆角动画比例、裁剪边界、缩放和锁屏高度限制，关闭时重建原生路径并恢复样式。

`GradientDrawable` 的宿主扩展枚举与插件不同：`IGradientDrawableExt.setSmoothRoundStyle` 的 G2 类型为 1，插件 Triple / SDK 为 2。设备真实扩展的 `isG2SmoothType` 已确认该映射，避免只写入同一数字却选择不同曲线。前景恢复保存实际扩展样式与权重，且继续使用同步重入保护。

新增 `CloudCurveCheck` 覆盖上述类型、权重、半径与恢复；设备独立探针另外调用真实 `OplusPathAdapter` 的 G2 不对称圆角路径和真实 GradientDrawable 扩展。所有图形效果由宿主原生路径、Drawable 与 shader 绘制。


最终曲线修复通过完整效果回归（新增 22 项跨宿主曲线用例及 11 项同步重入用例）、111 项 release 单元测试和 release lint（0 错误、59 条现有警告）。签名 APK 已覆盖安装；通过模块现有重启入口重新加载 SystemUI，系统设置进程关闭后在下次打开时加载新版入口。最终 GPU 外观仍需要用户在实际通知中心/锁屏流体云场景复查，不能将参数测试当成所有模板的像素验收。


最终设备加载检查：SystemUI 新进程 PID 14818 已注册 `notification-card-cloud-clip`、`cloud-lock-capsule-smooth-path`、`cloud-lock-capsule-smooth-stroke`、`qs-corner-media-light`，运行报告 error 为空。检查期间没有新增 SystemUI tombstone；最近的 tombstone_23 来自之前独立探针启动失败，进程不是 SystemUI。该观察仅表示已加载和当前未记录崩溃，不作为所有模板视觉验收结论。


## 锁屏卡片收缩为胶囊时的最终作用域修正

用户随后确认实时生效正常，保留原有配置同步链。设备验证也显示同一个 SystemUI PID 14818 收到新 revision，无需重启即可更新；临时用于测试的面板饱和度已从 131 恢复为 130。

锁屏缩小后仍变成小圆角的根因是作用域过宽：先前把展开卡片半径直接写入 `CapsuleItemView.getActualRadius/getMaxRadius`、原生内容路径及 StrokeLayer。固件的 `CapsuleSeedingData` 持有 `miniCardView`，`CapsuleItemView` 自行完成分段收缩与圆角切换；将卡片半径套到该容器，会覆盖系统胶囊两端的半圆和动画几何。

最终移除上述迷你胶囊形状 Hook。`CAPSULE` 材质仍可使用锁屏模糊/混色，但圆角由系统控制；`CUSTOM` 展开卡片和独立的 LockScreen / SecondaryLockScreen 展开 SDK 卡片继续使用自定义圆角。模板、共享背景和 SDK 会检查实际父级归属；进入原生胶囊的内容保留原生几何，复用已修改内容时，在首个轮廓/SDK 布局回调中恢复原生缓存，无需再次改配置或重启。

新增 `CloudCapsuleIsolationCheck` 在旧代码上直接复现 `card=0` 覆盖 native capsule 半径；修正后覆盖 0/8/13/64 dp、200/120/80/40/28 px 五种收缩高度、原生最大半径、背景路径/权重、描边、共享模板、SDK 重挂载和材质独立性。此前要求“迷你胶囊同步展开卡片半径”的回归已改为验证原生胶囊隔离。


本次最终验证：101 项展开卡片/胶囊隔离检查、完整效果回归、111 项 release 单元测试通过；release lint 0 错误、59 条现有警告，签名与对齐检查通过。产物 `dist/thunder-lock-capsule-fixed.apk`，旧 `thunder-corners-fixed.apk` 路径也同步为相同的新包，避免继续安装旧的胶囊覆盖方案。
