package io.github.baishangui.thunder

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.*
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.nav.core.NavBackStack
import io.github.suqi8.coui.kmp.nav.core.NavDisplay
import io.github.suqi8.coui.kmp.nav.core.NavKey
import io.github.suqi8.coui.kmp.nav.core.navBackStackOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data object SettingsHome : NavKey

private data class SettingsTabContent(val home: Int?, val section: WorkbenchSection?, val tile: String?)

private val SettingsBackStackSaver = Saver<NavBackStack, String>(
    save = { stack -> (stack.lastOrNull() as? SettingsDestination)?.let { "${it.page.name}|${it.parameterId.orEmpty()}" }.orEmpty() },
    restore = { route ->
        val page = SettingsPage.entries.firstOrNull { it.name == route.substringBefore('|') }
        val parameter = WorkbenchCatalog.parameter(route.substringAfter('|', ""))?.takeIf { it.section.page == page }
        if (page == null) navBackStackOf(SettingsHome)
        else navBackStackOf(SettingsHome, SettingsDestination(page, parameter?.id))
    },
)

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)
    private var bannerTestPending = false
    override fun onResume() { super.onResume(); resumeTick++; RemoteSettings.refreshScope() }
    override fun onStop() {
        super.onStop()
        if (bannerTestPending) {
            bannerTestPending = false
            TestNotification.postAfterAppStops(applicationContext)
        }
    }
    private fun startBannerTest(): String {
        TestNotification.prepare(this)?.let { return it }
        if (bannerTestPending) return "正在返回桌面，请稍候查看测试横幅"
        bannerTestPending = true
        if (!moveTaskToBack(true)) {
            bannerTestPending = false
            return "未能返回桌面，请重试横幅测试"
        }
        return "已安排横幅测试：返回桌面约 1 秒后发送，点击通知可回到 thunder"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val prefs = SettingsProvider.preferences(this)
        val workbenchPreferences = getSharedPreferences("workbench_shortcuts", MODE_PRIVATE)
        val appearancePreferences = getSharedPreferences("coui_appearance", MODE_PRIVATE)
        val loaded = Bundle().apply { prefs.all.forEach { (k,v) -> when(v) { is Boolean -> putBoolean(k,v); is Int -> putInt(k,v) } } }
        setContent {
            val editor = remember {
                val saved = Settings.from(loaded)
                SettingsEditorState(saved, SettingsWriter.latest ?: saved)
            }
            val draft = editor.draft
            val shortcuts = remember { WorkbenchShortcuts(workbenchPreferences) }
            val appearance = remember { AppAppearance(appearancePreferences) }
            var homeTab by rememberSaveable { mutableIntStateOf(0) }
            val backStack = rememberSaveable(saver = SettingsBackStackSaver) { navBackStackOf(SettingsHome) }
            fun openDestination(destination: SettingsDestination) {
                if (backStack.lastOrNull() == SettingsHome) backStack.add(destination)
            }
            fun open(page: SettingsPage) {
                openDestination(SettingsDestination(page))
            }
            fun closeDetail() {
                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            }
            var searching by rememberSaveable { mutableStateOf(false) }
            var query by rememberSaveable { mutableStateOf("") }
            val focus = LocalFocusManager.current
            fun stopSearch() {
                focus.clearFocus()
                searching = false
                query = ""
            }
            var restartFeedback by rememberSaveable { mutableStateOf(false) }
            var reset by rememberSaveable { mutableStateOf(false) }
            var imported by rememberSaveable(stateSaver=Settings.saver) { mutableStateOf(Settings()) }
            var importConfirm by rememberSaveable { mutableStateOf(false) }
            var exportText by rememberSaveable { mutableStateOf("") }
            var message by rememberSaveable { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var refresh by remember { mutableIntStateOf(0) }
            val restartState by ScopeRestart.restartState.collectAsState()
            val rootState by RootAccess.state.collectAsState()
            LaunchedEffect(restartState) {
                if (restartState.message.isNotEmpty()) { message=restartState.message; refresh++ }
            }
            val scope=rememberCoroutineScope()
            val dark=when(draft.theme) { 1->false; 2->true; else->isSystemInDarkTheme() }
            SideEffect { WindowCompat.getInsetsController(window,window.decorView).apply {
                isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark
            } }
            val history = remember { ControlHistory() }
            val writeResult by SettingsWriter.result.collectAsState()
            SideEffect {
                editor.pendingWrite()?.let { SettingsWriter.submit(this@MainActivity, it) }
            }
            LaunchedEffect(writeResult) {
                writeResult.settings?.let { editor.acceptSaved(it, SettingsWriter.latest) }
                if (writeResult.error != null) message = "自动保存失败：${writeResult.error}"
            }
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(message) { if (message.isNotEmpty()) { snackbar.showSnackbar(message); message = "" } }
            val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                message=if(granted) startBannerTest() else "未允许通知权限，暂时无法发送测试通知。可再次点击测试或在系统设置中开启通知。"
            }
            fun testNotification() {
                if(TestNotification.permissionGranted(this@MainActivity)) message=startBannerTest()
                else notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if(uri!=null) { busy=true;scope.launch {
                    val result=withContext(Dispatchers.IO) { runCatching {
                        val bytes=contentResolver.openInputStream(uri)?.use { it.readNBytes(65537) } ?: error("无法读取文件")
                        require(bytes.size<=65536) { "配置不能超过 64 KB" };Settings.parse(bytes.toString(Charsets.UTF_8))
                    } }
                    busy=false;result.onSuccess { imported=it;importConfirm=true }.onFailure { message="导入失败：${it.message ?: "配置格式错误"}" }
                } }
            }
            val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if(uri!=null) { val snapshot=exportText;busy=true;scope.launch {
                    val result=withContext(Dispatchers.IO) { runCatching {
                        contentResolver.openOutputStream(uri,"wt")?.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) } ?: error("无法写入文件")
                    } }
                    busy=false;message=if(result.isSuccess) "配置已导出" else "导出失败：${result.exceptionOrNull()?.message}"
                } }
            }
            AppTheme(draft, dark, appearance) {
                val labels = listOf(stringResource(R.string.workbench_functions), stringResource(R.string.workbench_favorites),
                    stringResource(R.string.workbench_management))
                val screen: @Composable (SettingsDestination?) -> Unit = { destination ->
                    val currentDetail = destination?.page
                    val home = currentDetail == null
                    val active = backStack.lastOrNull() == (destination ?: SettingsHome)
                    val searchState = rememberTopAppBarSearchState(active = home && searching)
                    if (home) BackHandler(enabled = active && !searching && homeTab != 0) { homeTab = 0 }
                    val scrollStates = if (home) listOf(rememberScrollState(), rememberScrollState(), rememberScrollState()) else emptyList()
                    val sections = remember(currentDetail) { WorkbenchCatalog.sections(currentDetail) }
                    val initialTarget = remember(destination) { WorkbenchCatalog.parameter(destination?.parameterId) }
                    var section by rememberSaveable { mutableStateOf(initialTarget?.section ?: sections.firstOrNull()) }
                    var tile by rememberSaveable { mutableStateOf(initialTarget?.group?.takeIf { it in WorkbenchCatalog.tileGroups }
                        ?: WorkbenchCatalog.tileGroups.first()) }
                    var target by rememberSaveable { mutableStateOf(initialTarget?.id) }
                    val tabContent = SettingsTabContent(homeTab.takeIf { home }, section, tile.takeIf { section == WorkbenchSection.Tiles })
                    CompositionLocalProvider(LocalControlScope provides (currentDetail?.name ?: "home-$homeTab")) {
                        SettingsScaffold(title = currentDetail?.title ?: labels[homeTab], home = home, snackbar = snackbar,
                            back = if (home) null else ::closeDetail,
                            searchState = searchState.takeIf { home },
                            toolbarContent = {
                                if (home) TopAppBarSearchBar(query, { query = it }, searching, { expanded ->
                                    if (expanded) searching = true else stopSearch()
                                }, cancelText = stringResource(R.string.coui_cancel), label = stringResource(R.string.coui_search_settings),
                                    onSearch = { focus.clearFocus() })
                                else section?.let { selected ->
                                    WorkbenchTabs(sections, selected) { section = it; target = null }
                                }
                            }, actions = {
                                if (home || section == WorkbenchSection.StatusIcons) IconButton(onClick = {
                                    if (!restartState.running) ScopeRestart.restart(this@MainActivity, draft)
                                    restartFeedback = true
                                }, enabled = !busy) { Icon(COUIIcons.Refresh, "重启系统界面") }
                            }, bottomBar = {
                                if (home && !searching) FloatingNavigationBar {
                                    FloatingNavigationBarItem(homeTab == 0, { homeTab = 0 }, COUIIcons.GridView, labels[0])
                                    FloatingNavigationBarItem(homeTab == 1, { homeTab = 1 }, COUIIcons.Favorites, labels[1])
                                    FloatingNavigationBarItem(homeTab == 2, { homeTab = 2 }, COUIIcons.Settings, labels[2])
                                }
                            }) { padding ->
                            if (searchState.isContentVisible || !home) Box(
                                Modifier.fillMaxSize().then(if (home) Modifier.topAppBarSearchContent(searchState) else Modifier),
                                contentAlignment = Alignment.TopCenter) {
                                Crossfade(targetState = tabContent, animationSpec = tween(150), label = "Settings tabs",
                                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()) { visible ->
                                    val (visibleHome, visibleSection, visibleTile) = visible
                                    val pageScroll = if (visibleHome != null) scrollStates[visibleHome] else rememberScrollState()
                                    val insets = Modifier.padding(padding).consumeWindowInsets(padding)
                                    val interactive = if (visible == tabContent) Modifier else Modifier
                                        .semantics { hideFromAccessibility() }
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                            }
                                        }
                                    Column(Modifier.fillMaxSize().then(interactive)
                                        .then(if (visibleSection != null) insets else Modifier).verticalScroll(pageScroll)
                                        .then(if (visibleSection == null) insets else Modifier)
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                        if (currentDetail != null) {
                                            if (section == null) UiText(currentDetail.description, muted = true)
                                            when (currentDetail) {
                                                SettingsPage.Appearance -> AppearanceSettings(draft, appearance) { editor.draft = it }
                                                SettingsPage.Configuration -> {
                                                    SettingsMenuGroup("备份与迁移") {
                                                        item { BasicComponent(title = "导出配置", summary = "保存一份 JSON 备份",
                                                            startAction = { SettingsIcon(COUIIcons.Share) }, enabled = !busy, onClick = {
                                                            exportText = draft.json()
                                                            exportLauncher.launch("thunder-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.json")
                                                        }) }
                                                        item { BasicComponent(title = "从 JSON 导入", summary = "先预览，再替换当前参数",
                                                            startAction = { SettingsIcon(COUIIcons.Import) }, enabled = !busy, onClick = {
                                                                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                                                            }) }
                                                    }
                                                    SettingsMenuGroup("恢复默认") {
                                                        item { BasicComponent(title = "恢复全部默认设置", summary = "恢复界面外观和全部效果参数",
                                                            startAction = { SettingsIcon(COUIIcons.Reset) }, enabled = !busy, onClick = { reset = true }) }
                                                    }
                                                }
                                                SettingsPage.Runtime -> {
                                                    val status = remember(resumeTick, refresh, RemoteSettings.activation, RemoteSettings.status, RemoteSettings.scopeStatus, rootState) { runtimeStatus() }
                                                    SettingsGroup {
                                                        UiText(status.first, 18, bold = true)
                                                        UiText(status.second, 12, muted = true)
                                                    }
                                                    SettingsAction("刷新状态", Modifier.fillMaxWidth(), enabled = !busy, leadingIcon = COUIIcons.Refresh) { RemoteSettings.refreshScope(); refresh++ }
                                                    SettingsAction("复制运行报告", Modifier.fillMaxWidth(), leadingIcon = COUIIcons.Copy) {
                                                        val text = "thunder ${BuildConfig.VERSION_NAME} · ${android.os.Build.DISPLAY}\n${runtimeStatus().second}"
                                                        getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("thunder 运行报告", text))
                                                        message = "运行报告已复制"
                                                    }
                                                }
                                                SettingsPage.About -> SettingsGroup {
                                                    UiText("thunder", 24, bold = true)
                                                    Summary("版本", BuildConfig.VERSION_NAME)
                                                    Summary("适配系统", "ColorOS 17")
                                                    UiText("COUI 2.0.0 · LSPosed API 102", 13, muted = true)
                                                }
                                                else -> visibleSection?.let { selected ->
                                                    WorkbenchEditor(selected, visibleTile ?: WorkbenchCatalog.tileGroups.first(),
                                                        { tile = it; target = null }, target.takeIf { visible == tabContent },
                                                        { target = null }, draft, { editor.draft = it }, shortcuts, ::testNotification)
                                                }
                                            }
                                        } else when(visibleHome) {
                                            0 -> FunctionWorkbench(draft, { editor.draft = it }, ::openDestination)
                                            1 -> FavoritesWorkbench(draft, shortcuts, { editor.draft = it }, ::openDestination, { homeTab = 0 })
                                            else -> {
                                                val activation = RemoteSettings.activation
                                                PreferencesMenu(draft, ::open)
                                                SettingsMenuGroup("模块与权限") {
                                                    item { ModuleStatusRow("LSPosed", activation.label,
                                                        if (activation != RemoteSettings.Activation.Connected) activation.hint else "",
                                                        activation == RemoteSettings.Activation.Connected) }
                                                    item { ModuleStatusRow("作用域", if (RemoteSettings.scopeStatus.startsWith("作用域已完整")) "已完整启用" else "待检查",
                                                        if (RemoteSettings.scopeStatus.startsWith("作用域已完整")) "" else RemoteSettings.scopeStatus,
                                                        RemoteSettings.scopeStatus.startsWith("作用域已完整")) }
                                                    item {
                                                        val checking = rootState == RootAccessState.Checking
                                                        ModuleStatusRow("Root", rootState.label,
                                                        if (rootState !in setOf(RootAccessState.Unchecked, RootAccessState.Granted)) rootState.hint else "",
                                                        rootState == RootAccessState.Granted, checking,
                                                        verify = if (checking || restartState.running) null else ({ RootAccess.verify() }))
                                                    }
                                                }
                                                SettingsMenuGroup("诊断与关于") {
                                                    item { SettingsMenuItem("运行报告",
                                                        remember(resumeTick, refresh, activation, RemoteSettings.status, RemoteSettings.scopeStatus, rootState) { runtimeStatus() }.first,
                                                        COUIIcons.Report) { open(SettingsPage.Runtime) } }
                                                    item { SettingsMenuItem("关于 thunder", "版本 ${BuildConfig.VERSION_NAME}", COUIIcons.Info) { open(SettingsPage.About) } }
                                                }
                                            }
                                        }
                                    }
                                    }
                            }
                            if (home && searchState.isResultsVisible) {
                                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
                                    .topAppBarSearchResults(searchState),
                                    contentAlignment = Alignment.TopCenter) {
                                    WorkbenchSearchResults(query, draft) { target -> focus.clearFocus(); openDestination(target) }
                                }
                            }
                            if(active && restartFeedback) SettingsDialog(
                                if(restartState.running) "正在重启系统界面" else "重启结果",
                                {restartFeedback=false},
                                positive = DialogButtonBarAction(if (restartState.running) "收起" else "完成", recommended = true) { restartFeedback = false }
                            ) {
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                                    if(restartState.running) InfiniteProgressIndicator(Modifier.size(24.dp))
                                    UiText(restartState.message,14,modifier=Modifier.weight(1f))
                                }
                            }
                            if(active && reset) SettingsDialog("恢复默认设置", { reset = false },
                                negative = DialogButtonBarAction("取消") { reset = false },
                                neutral = DialogButtonBarAction("恢复") { editor.draft = Settings(); reset = false }) {
                                UiText("将界面外观和效果参数恢复为默认值，并自动保存。",14,muted=true)
                            }
                            if(active && importConfirm) SettingsDialog("导入此配置？", { importConfirm = false },
                                negative = DialogButtonBarAction("取消") { importConfirm = false },
                                positive = DialogButtonBarAction("导入", recommended = true) {
                                    editor.draft = imported; importConfirm = false; message = "已导入，正在自动保存"
                                }) {
                                UiText("导入后会替换当前配置并自动保存。",14,muted=true)
                                Summary("下拉模糊",if(imported.panel.blur) "${imported.panel.radius} px" else "系统默认")
                                Summary("压暗 / 混色",if(imported.panel.tint) "${alphaPercent(imported.panel.color)}%" else "系统默认")
                                Summary("横幅背景混色",if(imported.banner.tint) "${alphaPercent(imported.banner.color)}%" else "系统默认")
                                Summary("磁贴背景混色",if(imported.cardArea.tint) "${alphaPercent(imported.cardArea.color)}%" else "系统默认")
                                Summary("通知条背景混色",if(imported.notificationArea.tint) "${alphaPercent(imported.notificationArea.color)}%" else "系统默认")
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalControlHistory provides history) {
                    NavDisplay(backStack = backStack, modifier = Modifier.fillMaxSize(), onBack = ::closeDetail) {
                        entry<SettingsHome>(contentKey = { "home" }) { screen(null) }
                        entry<SettingsDestination>(contentKey = { "${it.page.name}|${it.parameterId.orEmpty()}" }) { screen(it) }
                    }
                }
            }
        }
    }
    private fun runtimeStatus(): Pair<String,String> {
        val cloud=createDeviceProtectedStorageContext().getSharedPreferences("runtime_cloud",0)
        val cloudTime=cloud.getLong("time",0)
        val cloudReport=if(cloudTime>0L && cloud.getLong("elapsed",-1)<=android.os.SystemClock.elapsedRealtime())
            "\n插件独立进程 PID ${cloud.getInt("pid",-1)} · Hook ${cloud.getInt("hook_version",0)} · 配置 ${cloud.getLong("revision",-1)}\n插件报告 ${SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(Date(cloudTime))}\n插件命中：${cloud.getString("hits", "")}\n插件参数：${cloud.getString("effective", "")}\n插件异常：${cloud.getString("error", "")}" else ""
        val frameworkReport="LSPosed 激活：${RemoteSettings.activation.label}\n${RemoteSettings.status}\n${RemoteSettings.scopeStatus}\nRoot 权限：${RootAccess.state.value.label}"
        val r=createDeviceProtectedStorageContext().getSharedPreferences("runtime",0)
        val time=r.getLong("time",0)
        val hookVersion=r.getInt("hook_version",0)
        val reportElapsed=r.getLong("elapsed",-1)
        if(reportElapsed>android.os.SystemClock.elapsedRealtime()) return "等待本次开机报告" to "$frameworkReport\n系统界面记录来自上次开机，请同步配置并刷新。"
        if(time==0L) return "等待系统界面报告" to "$frameworkReport$cloudReport\n首次启用或升级模块后请正常重启手机，然后下拉通知或触发横幅，再刷新查看。"
        val error=r.getString("error","").orEmpty()
        val pending=r.getLong("revision",-1)!=SettingsProvider.preferences(this).getLong("revision",0)
        val title=when { hookVersion!=BuildConfig.VERSION_CODE->"等待本版 Hook 启动"; error.isNotEmpty()->"部分接口需要检查";pending->"等待系统界面读取配置";else->"已收到系统界面报告" }
        val hits=r.getString("hits","").orEmpty().ifEmpty { "尚未触发" }
        return title to "$frameworkReport\nSystemUI PID ${r.getInt("pid",-1)} · Hook $hookVersion · 配置 ${r.getLong("revision",-1)}\n最近报告 ${SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(Date(time))}\n命中：$hits\n实际参数：${r.getString("effective", "尚未触发绘制")}"+(if(error.isNotEmpty()) "\n异常：$error" else "")+cloudReport
    }

}

private fun alphaPercent(c: Int)=TintColor.percent(c)
@Composable private fun ModuleStatusRow(title: String, status: String, hint: String, ready: Boolean,
    checking: Boolean = false, verify: (() -> Unit)? = null) {
    BasicComponent(
        title = title,
        summary = hint.takeIf(String::isNotBlank),
        startAction = {
            SettingsIcon(when (title) {
                "LSPosed" -> COUIIcons.Link
                "作用域" -> COUIIcons.GridView
                else -> COUIIcons.Lock
            }, active = ready)
        },
        endActions = {
            if (checking) InfiniteProgressIndicator(Modifier.size(20.dp))
            else if (verify != null) TextButton("验证", onClick = verify)
            else UiText(status, 13, muted = !ready)
        },
        bottomAction = if (verify != null) ({ UiText(status, 13, muted = !ready) }) else null,
    )
}
@Composable private fun Summary(title: String,value: String) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        UiText(title,13,muted=true,modifier=Modifier.padding(end=12.dp))
        UiText(value,13,bold=true)
    }
}
