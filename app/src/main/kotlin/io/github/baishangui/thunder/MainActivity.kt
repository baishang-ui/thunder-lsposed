package io.github.baishangui.thunder

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.Manifest
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
        val loaded = Bundle().apply { prefs.all.forEach { (k,v) -> when(v) { is Boolean -> putBoolean(k,v); is Int -> putInt(k,v) } } }
        setContent {
            val editor = remember {
                val saved = Settings.from(loaded)
                SettingsEditorState(saved, SettingsWriter.latest ?: saved)
            }
            val draft = editor.draft
            var page by rememberSaveable { mutableIntStateOf(0) }
            var detail by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
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
            // Reserve the same font-aware header space on every route, before the first
            // layout. Measuring the current title/subtitle and feeding it into padding
            // moved both screens vertically one frame after navigation.
            val headerHeight = with(LocalDensity.current) {
                ((22 * 1.45f).sp.toDp() + (11 * 1.45f).sp.toDp() * 2 + 24.dp).coerceAtLeast(88.dp)
            }
            val navigationHeight = floatingNavHeight()
            val wallpaper=rememberLayerBackdrop(); val content=rememberLayerBackdrop()
            val overlaySource=rememberCombinedBackdrop(wallpaper,content)
            val themeController = rememberAppTheme(draft, dark)
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
            LaunchedEffect(message) {
                if (message.isNotEmpty()) { delay(5000); message = "" }
            }
            fun navigateBack() {
                if (detail != null) detail = detail?.parent
                else if (page != 0) page = 0
                else { SettingsWriter.submit(this@MainActivity, draft, immediate = true); finish() }
            }
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
            MiuixTheme(controller = themeController) {
            val style = GlassStyle(wallpaper, dark, draft, MiuixTheme.colorScheme)
            CompositionLocalProvider(LocalGlass provides style,
                LocalLiquidAppearance provides LiquidAppearance(dark, style.accent, draft.glassStrength / 60f, draft.refraction / 50f),
                LocalControlHistory provides history,
                LocalControlScope provides (detail?.name ?: "page-$page")) {
                Box(Modifier.fillMaxSize()) {
                PredictiveBackSurface(enabled = detail != null || page != 0, onBack = { navigateBack() }) {
                    Wallpaper(Modifier.matchParentSize().layerBackdrop(wallpaper),style)
                    Box(Modifier.fillMaxSize().layerBackdrop(content)) {
                        val scroll0=rememberScrollState();val scroll1=rememberScrollState()
                        AnimatedMenuContent(MenuDestination(page, detail), Modifier.fillMaxSize()) { destination ->
                            CompositionLocalProvider(LocalControlScope provides (destination.detail?.name ?: "page-${destination.page}")) {
                                val detailScroll = rememberScrollState()
                                Column(Modifier.widthIn(max=640.dp).fillMaxSize()
                                    .verticalScroll(if (destination.detail != null) detailScroll else when(destination.page) { 0->scroll0;else->scroll1 })
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                    .statusBarsPadding().padding(start=16.dp,end=16.dp,top=headerHeight,bottom=if (destination.detail == null) navigationHeight + 48.dp else 28.dp).navigationBarsPadding(),
                                    verticalArrangement=Arrangement.spacedBy(20.dp)) {
                                    val currentDetail = destination.detail
                                    if (currentDetail != null) {
                                        when (currentDetail) {
                                            SettingsPage.Appearance -> AppearanceSettings(draft) { editor.draft = it }
                                            SettingsPage.Configuration -> {
                                                SettingsSection("导入与导出") {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        LiquidAction("导入配置", Modifier.weight(1f), enabled = !busy) { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                                                        LiquidAction("导出配置", Modifier.weight(1f), enabled = !busy) {
                                                            exportText = draft.json()
                                                            exportLauncher.launch("thunder-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.json")
                                                        }
                                                    }
                                                }
                                                SettingsSection("恢复默认") {
                                                    UiText("恢复界面外观和全部效果参数。", 13, muted = true)
                                                    LiquidAction("恢复全部默认设置", Modifier.fillMaxWidth(), enabled = !busy) { reset = true }
                                                }
                                            }
                                            SettingsPage.Runtime -> {
                                                val status = remember(resumeTick, refresh, RemoteSettings.activation, RemoteSettings.status, RemoteSettings.scopeStatus, rootState) { runtimeStatus() }
                                                SettingsGroup {
                                                    UiText(status.first, 18, bold = true)
                                                    UiText(status.second, 12, muted = true)
                                                }
                                                LiquidAction("刷新状态", Modifier.fillMaxWidth(), enabled = !busy) { RemoteSettings.refreshScope(); refresh++ }
                                                LiquidAction("复制运行报告", Modifier.fillMaxWidth()) {
                                                    val text = "thunder ${BuildConfig.VERSION_NAME} · ${android.os.Build.DISPLAY}\n${runtimeStatus().second}"
                                                    getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("thunder 运行报告", text))
                                                    message = "运行报告已复制"
                                                }
                                            }
                                            SettingsPage.About -> SettingsGroup {
                                                UiText("thunder", 24, bold = true)
                                                Summary("版本", BuildConfig.VERSION_NAME)
                                                Summary("适配系统", "ColorOS 17")
                                                UiText("LSPosed API 102\nMiuix UI & Icons 0.9.0\nLiquid Glass · Kyant0 Backdrop 1.0.6", 13, muted = true)
                                            }
                                            else -> AdjustmentDetails(currentDetail, draft, { editor.draft = it }, { testNotification() }, { detail = it })
                                        }
                                    } else when(destination.page) {
                                        0 -> {
                                            SettingsGroup {
                                                LiquidSwitchRow("启用效果调整", "ColorOS 17", draft.enabled) { editor.draft = draft.copy(enabled = it) }
                                            }
                                            AdjustmentMenu { detail = it }
                                        }
                                        else -> {
                                            val activation = RemoteSettings.activation
                                            SettingsMenuGroup("模块") {
                                                SettingsMenuItem("运行报告",
                                                    remember(resumeTick, refresh, RemoteSettings.status, RemoteSettings.scopeStatus) { runtimeStatus() }.first,
                                                    MiuixIcons.Regular.Report) { detail = SettingsPage.Runtime }
                                            }
                                            SettingsSection("模块与权限") {
                                                ModuleStatusRow("LSPosed", activation.label,
                                                    if (activation != RemoteSettings.Activation.Connected) activation.hint else "",
                                                    activation == RemoteSettings.Activation.Connected)
                                                SettingsDivider()
                                                ModuleStatusRow("作用域", if (RemoteSettings.scopeStatus.startsWith("作用域已完整")) "已完整启用" else "待检查",
                                                    if (RemoteSettings.scopeStatus.startsWith("作用域已完整")) "" else RemoteSettings.scopeStatus,
                                                    RemoteSettings.scopeStatus.startsWith("作用域已完整"))
                                                SettingsDivider()
                                                ModuleStatusRow("Root", rootState.label,
                                                    if (rootState !in setOf(RootAccessState.Unchecked, RootAccessState.Granted)) rootState.hint else "",
                                                    rootState == RootAccessState.Granted, rootState == RootAccessState.Checking)
                                                LiquidAction(if (rootState == RootAccessState.Checking) "正在验证…" else "验证 Root 权限",
                                                    Modifier.fillMaxWidth(), enabled = rootState != RootAccessState.Checking && !restartState.running) { RootAccess.verify() }
                                            }
                                            PreferencesMenu(draft) { detail = it }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    ProgressiveHeader(overlaySource, Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .height(headerHeight + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()))
                    Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Color.Transparent) {
                        Box(Modifier.statusBarsPadding()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                            contentAlignment = Alignment.TopCenter) {
                            SmallTopAppBar(
                                title = detail?.title ?: listOf("调节", "设置")[page],
                                subtitle = detail?.category.orEmpty(),
                                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().height(headerHeight),
                                color = Color.Transparent,
                                defaultWindowInsetsPadding = false,
                                navigationIcon = {
                                    Spacer(Modifier.size(48.dp))
                                },
                                actions = {
                                    Spacer(Modifier.size(48.dp))
                                }
                            )
                        }
                    }
                    AnimatedVisibility(visible = detail == null, modifier = Modifier.align(Alignment.BottomCenter),
                        enter = fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 2 },
                        exit = fadeOut(tween(180)) + slideOutVertically(tween(280)) { it / 2 }) {
                        CompositionLocalProvider(LocalGlass provides style.copy(source=overlaySource)) {
                            FloatingNav(page,{ if (detail == null) page=it },Modifier
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                .navigationBarsPadding().padding(horizontal=28.dp,vertical=14.dp))
                        }
                    }
                    AnimatedVisibility(visible = message.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .navigationBarsPadding().padding(horizontal = 16.dp, vertical = if (detail == null) navigationHeight + 36.dp else 16.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }) {
                        SettingsGroup(Modifier.widthIn(max = 600.dp)) {
                            UiText(message, 13, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if(restartFeedback) SettingsDialog(
                        if(restartState.running) "正在重启系统界面" else "重启结果",
                        {restartFeedback=false},
                        footer = {
                            LiquidAction(if(restartState.running) "收起" else "完成",Modifier.fillMaxWidth(),selected=true) { restartFeedback=false }
                        }
                    ) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            if(restartState.running) InfiniteProgressIndicator(Modifier.size(24.dp))
                            UiText(restartState.message,14,modifier=Modifier.weight(1f))
                        }
                    }
                    if(reset) SettingsDialog("恢复默认设置",{reset=false}, footer = {
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            LiquidAction("取消",Modifier.weight(1f)) { reset=false }
                            LiquidAction("恢复",Modifier.weight(1f),selected=true) { editor.draft=Settings();reset=false }
                        }
                    }) {
                        UiText("将界面外观和效果参数恢复为默认值，并自动保存。",14,muted=true)
                    }
                    if(importConfirm) SettingsDialog("导入此配置？",{importConfirm=false}, footer = {
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            LiquidAction("取消",Modifier.weight(1f)) { importConfirm=false }
                            LiquidAction("导入",Modifier.weight(1f),selected=true) { editor.draft=imported;importConfirm=false;message="已导入，正在自动保存" }
                        }
                    }) {
                        UiText("导入后会替换当前配置并自动保存。",14,muted=true)
                        Summary("下拉模糊",if(imported.panel.blur) "${imported.panel.radius} px" else "系统默认")
                        Summary("压暗 / 混色",if(imported.panel.tint) "${alphaPercent(imported.panel.color)}%" else "系统默认")
                        Summary("横幅背景混色",if(imported.banner.tint) "${alphaPercent(imported.banner.color)}%" else "系统默认")
                        Summary("磁贴背景混色",if(imported.cardArea.tint) "${alphaPercent(imported.cardArea.color)}%" else "系统默认")
                        Summary("通知条背景混色",if(imported.notificationArea.tint) "${alphaPercent(imported.notificationArea.color)}%" else "系统默认")
                    }
                }
                // App chrome stays above the page, header backdrop and predictive-back
                // transform. TopAppBar's subtitle measurement must not position these buttons.
                Row(Modifier.align(Alignment.TopCenter).zIndex(1f)
                    .widthIn(max = 640.dp).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .statusBarsPadding().height(headerHeight).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (detail != null || page != 0) {
                            LiquidAction("返回", Modifier.size(48.dp), icon = MiuixIcons.Regular.Back) { navigateBack() }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    LiquidAction("重启系统界面", Modifier.size(48.dp), enabled = !busy,
                        selected = restartState.running, icon = MiuixIcons.Regular.Refresh) {
                        if (!restartState.running) ScopeRestart.restart(this@MainActivity, draft)
                        restartFeedback = true
                    }
                }
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
@Composable private fun ModuleStatusRow(title: String, status: String, hint: String, ready: Boolean, checking: Boolean = false) {
    BasicComponent(
        title = title,
        summary = hint.takeIf { it.isNotEmpty() },
        insideMargin = PaddingValues(vertical = 8.dp),
        startAction = {
            if (checking) InfiniteProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp))
            else Icon(MiuixIcons.Regular.Info, contentDescription = null,
                modifier = Modifier.padding(end = 12.dp).size(20.dp),
                tint = if (ready) LocalGlass.current.accent else LocalGlass.current.muted)
        },
        endActions = { UiText(status, 13, muted = !ready) }
    )
}
@Composable private fun Summary(title: String,value: String) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        UiText(title,13,muted=true,modifier=Modifier.padding(end=12.dp))
        UiText(value,13,bold=true)
    }
}
@Composable private fun Wallpaper(modifier: Modifier,s: GlassStyle) {
    Surface(modifier, color = s.background) {}
}

@Composable internal fun AreaControls(title: String,area: Area,blur: Boolean=true,corner: Boolean=true,
                                    cornerRange: IntRange=0..64,unifiedCorners: Boolean=false,
                                    onRestore: (() -> Unit)?=null,onChange: (Area)->Unit) {
    CompositionLocalProvider(LocalControlScope provides "${LocalControlScope.current}:$title") {
        SettingsSection(title) {
            if (blur) {
                LiquidSwitchRow("自定义模糊", "", area.blur) { onChange(area.copy(blur=it)) }
                if (area.blur) LiquidValueSlider("模糊半径",area.radius,0..200," px") { onChange(area.copy(radius=it)) }
                SettingsDivider()
            }
            LiquidSwitchRow("自定义混色", "", area.tint) { onChange(area.copy(tint=it)) }
            if (area.tint) LiquidColorSetting("混色颜色",area.color) { onChange(area.copy(color=it)) }
            if (corner) {
                SettingsDivider()
                LiquidSwitchRow("自定义圆角", if(unifiedCorners) "所有磁贴统一半径，与通知条使用相同的 dp 单位" else "", area.cornerEnabled) { onChange(area.copy(cornerEnabled=it)) }
                if (area.cornerEnabled) LiquidValueSlider("圆角半径",area.corner,cornerRange," dp") { onChange(area.copy(corner=it)) }
            }
            if (area.blur || area.tint || area.cornerEnabled || onRestore != null) {
                SettingsDivider()
                LiquidAction("恢复系统材质",Modifier.fillMaxWidth()) {
                    if (onRestore != null) onRestore() else onChange(area.copy(blur=false,tint=false,cornerEnabled=false))
                }
            }
        }
    }
}
