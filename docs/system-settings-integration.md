# 系统设置集成

目标为 ColorOS 17 / Android 17（SDK 37）。`HookEntry` 在 `com.android.settings` 主进程安装 `SystemSettingsHook`，在 `TopLevelSettings` 创建页面与启动时添加唯一的 thunder 设置项；同一 PreferenceScreen 不重复添加。入口位于根列表末尾的独立原生分组中，使用系统个性化图标和首页跳转布局。

入口与分类导航打开系统自己的 `com.android.settings.SubSettings`，承载系统自带的具体类 `com.coui.appcompat.preference.COUIPreferenceFragment`。Fragment 参数中的模块专用键决定分类；仅对携带此键的实例构建页面。没有替换宿主的 Fragment 校验、主题、返回导航或绘制，也不启动模块 Activity。系统原本的其他设置页继续执行原逻辑。

页面使用系统加载器与页面 Context 创建 `SettingsPreferenceCategory` 原生分组卡片、`SettingsSimpleJumpPreference` 跳转项、`SettingSwitchPreference` 开关，以及 `COUIPreference`、`RestrictedMenuPreference`、`OplusSeekBarPreference`；选项复用“自动息屏”的 `RestrictedMenuPreference` 原生浮动菜单；重置确认使用 `COUIAlertDialogBuilder`。所有模块 Preference 禁用宿主本地持久化。输入、颜色与重置对话框显式启用宿主背景模糊；保存及重置按钮使用 COUI 的推荐按钮路径，保留宿主材质、按压反馈与窗口动画，并遵循宿主动画等级和模糊降级策略。反射方法查找忽略协变返回值生成的编译器桥接方法，避免 COUI Builder 被误判为重载冲突。颜色通过宿主 `AtmosphereLightColorPickRound` 原生色环直接点选、拖选，并使用宿主颜色色块提供固定的原色、白色、浅灰、灰色、深灰、黑色色块，并标出当前选择；不使用 ARGB 通道滑块。不透明度通过同一颜色面板内的原生 COUISeekBar 连续调节，颜色预览同步反映透明度，选色保留现有 alpha；打开弹窗保留已有任意色值，仅点击“保存”后提交，取消不修改配置，页面重建时恢复未保存的颜色草稿。数值行使用 `COUIEditTextPreference`，点击后由宿主 `COUIEditTextPreferenceDialogFragment` 打开原生输入弹窗，提供数字键盘、“取消”和“保存”；空值、非整数及越界值显示原生输入错误状态并禁用保存。原有 `OplusSeekBarPreference`（内部为 COUISeekBar）滑动条继续保留，使用既有参数范围；滑块以零为起点映射到参数的最小值，松手后提交保存。系统设置跟随系统主题；原模块的预览和配色工具继续保留在 LSPosed 的模块设置入口。独立模块界面已按用户要求撤回 COUI 迁移，恢复 Miuix / Liquid Glass，不再从系统应用加载 COUI 类和主题。

首页按“界面与通知”“系统显示”分组，功能页按材质、尺寸、时钟等语义分组。目录提供分组、简短标题和单位；数值与选项通过 COUI 自带的 assignment 显示在右侧，详细模块状态保留在“配置与维护”。重启系统界面操作作为 COUIToolbar 右上角原生菜单按钮提供，各模块页面均可使用。纯 COUI Fragment 的列表根据 SubSettings 实际工具栏位置补充顶部布局间距，避免首项被工具栏覆盖。页面导航使用宿主 `coui_open_slide_enter` / `coui_open_slide_exit` 整页横向过渡，关闭列表自身的布局和条目动画，避免首次加载及条件显隐产生纵向滑入。仿照 SettingsBaseFragment 清空 AndroidX 的全宽分界线，仅保留 COUI 卡片内的原生缩进分界线。

逐页根据 Hook 生效条件管理下级选项：模糊、混色、圆角、尺寸、边距、饱和度未开启时收起对应参数；流体云总开关控制所有调整分组，胶囊尺寸与背景合并；时钟格式、日期等受自定义时钟控制；隐藏手势条时收起外观设置，材质和亮暗颜色同样受外观开关控制。空分组自动收起；模块总开关关闭时禁用其余参数，原参数保留。

`SystemSettingsCatalog` 是参数与原生控件之间的数据描述，覆盖全部 Hook 参数；兼容页自身的七个主题字段不在宿主内提供。隐藏图标的各位分别显示为开关，写入时只变更目标位。锁屏背景直接提供模糊、混色开关和下级参数，不另设“跟随系统 / 自定义”选择器。编辑锁屏材质时原子切换到规范值 2；旧配置值为 0 时，界面显示有效的关闭状态，不意外启用旧的材质参数。应用背景色只呈现一项强度，0% 关闭、100% 系统强度，写入时同时处理原有兼容标志。兼容字段保留读写与导入能力，不占用额外控件。

系统设置通过模块的 SettingsProvider 调用 `ui_snapshot`、`ui_update`、`ui_import`、`ui_reset`、`ui_restart`。Provider 校验调用 UID，仅模块自身和系统设置 UID 可调用这些操作；原有 SystemUI/插件读取和报告接口权限保持独立。Android 共享 UID 的系统组件具有相同的 UID 信任边界。补丁只允许目录内的键、正确类型、范围与图标位；配置导入通过 ACTION_OPEN_DOCUMENT 选择 JSON 文件，在工作线程读取，支持 UTF-8 BOM、限制 1 MB，并继续走原格式校验；取消选择不修改配置。读写与框架同步均留在模块 UID 下，写入串行复用 `SettingsWriter`，不向 Settings 进程申请 Root。

宿主侧的配置调用在串行工作线程执行。首次创建先根据目录建立禁用的原生控件，让原生子对话框恢复时能找到稳定的 Preference key；收到完整配置快照后才允许修改。后续快照更新既有控件，保留列表与原生弹窗。切换和写入失败显示提示与重试入口。宿主字段仅弱引用 Fragment；静态缓存只有 Bundle 数据，不保存 Activity/Context。

Manifest 同时保留桌面 `LAUNCHER` 入口和标准 `de.robv.android.xposed.category.MODULE_SETTINGS` 入口，原 Activity 可从桌面或框架管理器进入。独立 App 保留原有 Miuix / Liquid Glass 完整界面，系统设置集成是额外入口，不替换独立 App。模块仍以 APK 安装并运行配置服务。首次安装或升级后需手动启用“设置”、SystemUI、插件三个作用域并正常重启；APK 无权自动开启自身的 LSPosed 作用域。

验证分为三个层次：

- 单元测试：完整 Hook 字段覆盖、参数范围、类型与非法键拒绝、隐藏图标位保留、无关配置保留、锁屏编号、三作用域检查。
- 编译与静态检查：Debug 单元测试、Lint、Release/R8 构建，以及现有 Hook 检查。
- 连接真机的宿主组件探针：用设备当前 Settings.apk 的类与主题创建 10 个页面，检查所有参数可绑定且所有控件禁用宿主持久化。探针不替换已安装模块，不开启作用域，不改系统配置。它不等价于 LSPosed 入口与完整 Activity/Fragment 生命周期的端到端验收；新版安装并授权“设置”作用域后，还需验证入口显示、分类返回、开关实时同步、原生输入与选择弹窗、重建恢复和深浅色。

滑块采用原生拖动状态与按参数键排序的乐观回显，触摸期间不重绑滑块，旧请求应答不覆盖较新的拖动值；保存失败清除乐观状态并显示重试。颜色通道写入只修改对应 8 位，保留其余通道。
