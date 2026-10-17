# 应用 UI 审查与调整

## 2026-10-17：统一使用 MIUIx / Liquid Glass

依据根目录 `AGENTS.md`，应用交互控件统一复用 MIUIx 与 Kyant0 Liquid Glass，
页面布局和状态绑定保留为薄封装。本轮完成：

- 顶栏改用 MIUIx `SmallTopAppBar` / `Surface`，返回与重启操作继续使用 `LiquidButton`；
  页面顶部留白读取顶栏实际高度，移除应用自定义渐进模糊 Shader。
- 回退操作改用 `LiquidButton`；分割线改用 MIUIx `HorizontalDivider`；
  菜单图标容器、颜色样本与预览材质容器改用 MIUIx `Surface`。
- 预览比较、示例应用和音量展开选项复用 MIUIx 选项组件；
  窄屏与大字体继续使用纵向 `RadioButton` 设置行。
- 预览入口使用 MIUIx 可点击 `Card`，放大预览接入统一的 `WindowDialog`，
  操作区固定在底部，正文滚动；弹窗中可以切换音乐 / 出行与收起 / 展开。
- 音乐和音量进度展示改用 MIUIx `LinearProgressIndicator`；
  手绘关闭图标与音乐波形改为 Miuix Icons。
- SystemUI / PUI 原始图标作为图片内容展示，时钟和电量文字使用 MIUIx `Text`；
  应用 UI 目录不再使用 Compose Canvas、自定义 clickable 控件或 RuntimeShader。

以下为此前的审查记录；本轮验证结果见文末。

审查日期：2026-10-16。范围为 thunder 应用自身的 Compose 页面及其公共组件，
包括主页、调节分类、设置分类、全部二级设置页、图标预览、颜色选择、配置确认与重启反馈弹窗。

## 对照来源

按项目锁定的 **Miuix 0.9.0** 审查，没有以最新版 API 替换当前依赖。
同时核对了 Maven 发布的 `miuix-ui-android:0.9.0` sources JAR；其中 Card、BasicComponent
与以下 v0.9.0 源码一致。

- [官方设置页样例](https://github.com/compose-miuix-ui/miuix/blob/v0.9.0/example/shared/src/commonMain/kotlin/SettingsPage.kt)：卡片中的 Preference 与页面内容边距。
- [CardSection](https://github.com/compose-miuix-ui/miuix/blob/v0.9.0/example/shared/src/commonMain/kotlin/component/CardSection.kt)：自由内容通过 `insideMargin` 留白。
- [BasicComponentSection](https://github.com/compose-miuix-ui/miuix/blob/v0.9.0/example/shared/src/commonMain/kotlin/component/BasicComponentSection.kt) 和 [SwitchPreference](https://github.com/compose-miuix-ui/miuix/blob/v0.9.0/miuix-preference/src/commonMain/kotlin/top/yukonga/miuix/kmp/preference/SwitchPreference.kt)：标题、说明、起始图标与末端操作的布局方式。
- [TabRowSection](https://github.com/compose-miuix-ui/miuix/blob/v0.9.0/example/shared/src/commonMain/kotlin/component/TabRowSection.kt)：选择项使用库内控件，不自绘指示器。
- [ScaffoldDemo](https://github.com/compose-miuix-ui/miuix/blob/v0.9.0/docs/demo/src/commonMain/kotlin/ScaffoldDemo.kt)：页面内容应考虑顶栏与底栏占用。
- [WindowDialogDemo](https://github.com/compose-miuix-ui/miuix/blob/v0.9.0/docs/demo/src/commonMain/kotlin/WindowDialogDemo.kt) 和 [DialogSection](https://github.com/compose-miuix-ui/miuix/blob/v0.9.0/example/shared/src/commonMain/kotlin/component/DialogSection.kt)：保留库的窗口、关闭与系统 Insets 处理。
- `Card.kt`、`Component.kt`、`Text.kt`、`TabRow.kt`、`RadioButton.kt`、`WindowDialog.kt`、`DialogContentLayout.kt`：确认裁切层、文本样式、尺寸与键盘避让的实际实现。

## 原因与已落地的调整

Miuix 的 Card 是内容容器，会按照卡片形状裁切子内容；默认 `insideMargin` 为 0。
官方样例将两种用途区分开：放置已有内边距的 Preference 时直接放入 Card；放置自由文本时显式设置内容边距。
因此，组件库提供圆角与布局能力，并不能保护应用额外添加的裁切边界，也不能解除调用方设置的固定高度。

| 范围 | 发现 | 调整 |
| --- | --- | --- |
| 设置卡片 | 自由内容在嵌套 Column 中设置边距，公共卡片与内容边距分散 | 改由 Card 的 `insideMargin` 统一提供水平 18 dp、垂直 16 dp 留白 |
| 开关行 | 标题与说明单独裁成 12 dp 圆角，左侧和上下没有留白；回退按钮占用文本宽度 | 改用 Miuix `BasicComponent` 的内容区和末端操作区；裁切包围有内边距的整行，保留 LiquidToggle 的拖动反馈 |
| 颜色入口 | 色块、标题、HEX、箭头、回退按钮争用同一行宽度 | 使用 `BasicComponent`；HEX 放在标题下方；色块和箭头使用对应插槽 |
| 回退操作 | 编辑后挤压标题、数值和颜色编码 | 统一为独立的右侧回退区；无回退内容时不生成额外布局；操作标签包含对应设置名称 |
| 文字按钮 | 内部固定 48 dp 高；多行文本可能进入胶囊角部 | 改为最小 48 dp 高，增加内容边距；文字按钮使用 24 dp 圆角，随文本增高；图标按钮保留 48 dp 尺寸 |
| 单选设置 | 固定选择项尺寸无法适配较大的字体 | 按库内实际文本样式测量标签，配置 TabRow 高度与最小宽度；放不下全部选项时改为 Miuix RadioButton 的纵向设置行 |
| 滑条与状态摘要 | 标题和右侧数值使用固定的一行分配，窄屏时标题可能被挤到很窄 | 使用 FlowRow，在空间不足时换行；回退按钮不再占用数值区 |
| 浮动导航 | 导航与玻璃指示层固定高度；页面底部留白使用独立常量 | 根据图标、行高和留白计算导航高度，同时用于可见层、取样层和滑动指示层；页面与提示位置同步预留空间 |
| 横屏安全区 | 主要布局只预留上下系统栏 | 列表、标题操作区、底部导航和反馈提示增加水平方向 `safeDrawing` Insets |
| 弹窗 | 配置导入、恢复、重启反馈按钮随正文滚动 | 操作按钮统一放入固定 footer，正文独立滚动；继续使用 WindowDialog 自身的系统栏、键盘和关闭处理 |
| 图标预览 | 标签固定四列；示意状态栏始终一行 | 根据可用宽度和字体尺寸选择 1–4 列；标签居中，示意状态栏可换行 |
| 自定义主题 | 使用亮度 `.45` 决定黑白按钮文字，部分中等亮度背景对比度不足 | 复用已有 `AdaptiveTextColor.foreground` 的 WCAG 对比度算法；选中按钮使用实际强调色实底，使文字与计算背景一致 |
| 无障碍语义 | 开关文字与控件容易成为重复节点；部分操作缺少角色或状态 | 整行提供开关角色与状态，隐藏内部重复语义；补充回退按钮角色、选择组、滑条单位、输入错误与提示消息 live region |

## 页面覆盖

| 页面 | 审查重点 |
| --- | --- |
| 主页、运行报告 | 状态长文本、Root 状态、操作按钮、报告滚动 |
| 调节与设置分类 | Card 与 BasicComponent 的边距、菜单标题与摘要、分类标题语义 |
| 整体背景、磁贴、通知条、横幅 | 共享开关、滑条、颜色行、恢复按钮、回退 |
| 通知布局、手势条 | 开关说明和参数区域 |
| 流体云胶囊与卡片 | 共享总开关、条件参数、独立材质设置 |
| 音量面板 | 多行说明、尺寸与材质控件 |
| 状态图标显示与样式 | 长标题、单选项、预览网格和状态栏示意 |
| 外观 | 明暗与配色选项、HEX、圆角范围及玻璃设置 |
| 配置管理、关于 | 按钮、确认摘要、信息文本 |
| 颜色与反馈弹窗 | 正文滚动、固定操作区、HEX 错误、底层取样背景 |
| 页面过渡与返回 | 保留目的地独立内容、退出页面输入屏蔽、预测返回结束后的裁切复原 |

## 验证边界

### 启动闪退修复

玻璃控件限于 `LiquidButton`、`LiquidToggle`、`LiquidSlider`、`LiquidBottomTabs`，
其他 UI 控件继续使用 Miuix。此前给 `LiquidButton` 传入了 `miuixCapsuleShape()` /
`miuixShape(24.dp)`，它们返回的缓存形状只实现 Compose `Shape`；
Backdrop 1.0.6 的 `lens()` 只支持 `RoundedRectangularShape` 或 `CornerBasedShape`，
首页首次绘制按钮就会抛出 `UnsupportedOperationException`。

按钮改用 Kyant `Capsule` / `RoundedRectangle(24.dp)`，取样背景和按压裁剪继续共用
同一形状，保留透镜与平滑圆角。`LiquidButton` 参数限制为 `RoundedRectangularShape`，
不兼容的形状会在编译时被拒绝。`scripts/check_liquid_button.py` 检查编译后的参数约束，
已确认修复前失败，修复后通过；该检查也已加入构建工作流。

### 原审查记录

编译、Release 单元测试、Lint 与 APK 构建用于检查 API、现有逻辑回归和静态问题。
本轮执行 `:app:testReleaseUnitTest :app:lintRelease :app:assembleRelease` 成功：
65 项单元测试通过，0 失败、0 跳过；Lint 为 0 错误、28 项警告，未宣称警告清零。
已有 `ControlHistoryTest`、`PickerColorTest`、`MonetSettingsTest` 和 `AdaptiveTextColorTest`
覆盖本次复用的回退、取色、主题配置与文字对比度逻辑；这些测试不验证 Compose 像素布局。

当前 ADB 没有连接设备，也没有可用的运行中模拟器，本次没有进行真机截图对比、TalkBack 操作或动画观感验收。
以下项目仍需在目标 Android 17 / ColorOS 17 设备上确认，不能由构建通过替代：

- 320 / 360 / 411 dp 宽度及横屏下，默认、1.3 倍、2 倍字体的长说明和圆角边缘。
- 界面圆角为 16 / 28 / 36 dp 时，所有设置行首尾文字与按压高亮的边界。
- 修改后出现回退按钮、连续拖动、回退、切换页面并返回时的内容和滚动位置。
- 大字体时纵向单选行、浮动导航、滑动高亮和列表末项的可见性。
- 取色弹窗展开键盘、输入无效 HEX、关闭键盘、确认或取消，以及短窗口中的 footer。
- 浅色、深色、壁纸取色、自选取色与自定义强调色的实际材质对比度。
- TalkBack 的单个开关节点、选择状态、滑条单位、输入错误与反馈提示。

后续新增设置优先复用公共设置行和 Miuix 插槽；自由内容通过 Card 内容边距布局，
不在文字本身增加圆角裁切，不用固定高度约束可换行的文字区域。

## 2026-10-17 验证结果

本轮在独立构建目录 `.local/ui-library-build` 中执行 Debug 构建、Release 单元测试、
Release Lint 与 Release APK 构建，最终全部通过。Windows 测试环境补充 Git 的 `sh`
路径后，77 项单元测试全部通过，0 失败、0 跳过。Lint 为 0 错误、45 项警告；
这些警告未在本轮清零。LiquidButton 编译签名仍限制为 Backdrop 透镜支持的
`RoundedRectangularShape`。

检查应用 UI 源码目录，已无 Compose Canvas、自定义 clickable 控件、RuntimeShader、
原生 Compose Dialog 或 Material3 控件；官方 Liquid Glass 组件内部实现保持原有来源。

测试 APK 使用独立包名 `io.github.baishangui.thunder.previewqa`。本轮两次 ADB 安装
均未返回完成结果，已取消等待；未完成真机页面截图、触控、大字体或 TalkBack 验收，
不能用构建和单元测试结果代替这些视觉与交互检查。Release APK 为未签名构建产物。
