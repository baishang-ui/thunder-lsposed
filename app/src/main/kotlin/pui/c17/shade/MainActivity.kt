package pui.c17.shade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)
    override fun onResume() { super.onResume(); resumeTick++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val prefs = SettingsProvider.preferences(this)
        val loaded = Bundle().apply { prefs.all.forEach { (k,v) -> when(v) { is Boolean -> putBoolean(k,v); is Int -> putInt(k,v) } } }
        setContent {
            var saved by remember { mutableStateOf(Settings.from(loaded)) }
            var draft by rememberSaveable(stateSaver=Settings.saver) { mutableStateOf(saved) }
            var page by rememberSaveable { mutableIntStateOf(0) }
            var section by rememberSaveable { mutableIntStateOf(0) }
            var picker by rememberSaveable { mutableIntStateOf(-1) }
            var restart by rememberSaveable { mutableStateOf(false) }
            var reset by rememberSaveable { mutableStateOf(false) }
            var imported by rememberSaveable(stateSaver=Settings.saver) { mutableStateOf(Settings()) }
            var importConfirm by rememberSaveable { mutableStateOf(false) }
            var exportText by rememberSaveable { mutableStateOf("") }
            var message by rememberSaveable { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var refresh by remember { mutableIntStateOf(0) }
            var runtimeDetails by rememberSaveable { mutableStateOf(false) }
            val scope=rememberCoroutineScope()
            val dark=when(draft.theme) { 1->false; 2->true; else->isSystemInDarkTheme() }
            val wallpaper=rememberLayerBackdrop(); val content=rememberLayerBackdrop()
            val overlaySource=rememberCombinedBackdrop(wallpaper,content)
            val style=GlassStyle(wallpaper,dark,draft)
            val dirty=saved!=draft
            SideEffect { WindowCompat.getInsetsController(window,window.decorView).apply {
                isAppearanceLightStatusBars=!dark; isAppearanceLightNavigationBars=!dark
            } }
            fun save(after: Boolean=false) {
                if(busy) return
                val snapshot=draft; busy=true
                scope.launch {
                    var synced=false
                    val ok=withContext(Dispatchers.IO) {
                        val b=snapshot.bundle(); val edit=prefs.edit()
                        b.keySet().forEach { k -> @Suppress("DEPRECATION") when(val v=b.get(k)) { is Boolean->edit.putBoolean(k,v); is Int->edit.putInt(k,v) } }
                        val committed=edit.putLong("revision",maxOf(System.currentTimeMillis(),prefs.getLong("revision",0)+1)).commit()
                        if(committed) synced=RemoteSettings.publish(this@MainActivity)
                        committed
                    }
                    busy=false
                    if(ok) { saved=snapshot;refresh++;message=if(synced) "已保存并同步到 API 102，重启系统界面后应用" else "已保存在本地；${RemoteSettings.status}，请确认模块已启用后重新打开应用"; if(after && synced) restart=true }
                    else message="保存失败，请重试"
                }
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
            val colors = if(dark) darkColorScheme(primary=style.accent) else lightColorScheme(primary=style.accent)
            MiuixTheme(colors=colors) {
            CompositionLocalProvider(LocalGlass provides style,
                LocalLiquidAppearance provides LiquidAppearance(dark, style.accent, draft.glassStrength / 60f, draft.refraction / 50f)) {
                Box(Modifier.fillMaxSize()) {
                    Wallpaper(Modifier.matchParentSize().layerBackdrop(wallpaper),style)
                    Box(Modifier.fillMaxSize().layerBackdrop(content)) {
                        val scroll0=rememberScrollState();val scroll1=rememberScrollState();val scroll2=rememberScrollState()
                        Column(Modifier.align(Alignment.TopCenter).widthIn(max=640.dp).fillMaxSize()
                            .verticalScroll(when(page) { 0->scroll0;1->scroll1;else->scroll2 })
                            .statusBarsPadding().padding(start=20.dp,end=20.dp,top=100.dp,bottom=136.dp).navigationBarsPadding(),
                            verticalArrangement=Arrangement.spacedBy(18.dp)) {
                            when(page) {
                                0 -> {
                                    val status=remember(resumeTick,refresh,RemoteSettings.status) { runtimeStatus() }
                                    UiText("系统外观",13,muted=true)
                                    SettingsGroup {
                                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                            Box(Modifier.size(8.dp).background(if(status.first.startsWith("已收到")) Color(0xff5b9e76) else Color(0xffb78a53),RoundedCornerShape(8.dp)))
                                            UiText(status.first,17,bold=true)
                                        }
                                        LiquidSwitchRow("启用效果调整","ColorOS 17 · SystemUI",draft.enabled) { draft=draft.copy(enabled=it) }
                                        UiText(if(message.isNotEmpty()) message else "保存后重启系统界面，确认新进程读取配置。",13,muted=true)
                                        UiText(if(runtimeDetails) "收起运行报告" else "查看运行报告",13,color=style.accent,
                                            modifier=Modifier.clickable { runtimeDetails=!runtimeDetails })
                                        if(runtimeDetails) {
                                            UiText(status.second,12,muted=true)
                                            LiquidAction("复制运行报告",Modifier.fillMaxWidth()) {
                                                val text="thunder ${BuildConfig.VERSION_NAME} · ${android.os.Build.DISPLAY}\n${runtimeStatus().second}"
                                                getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("thunder 运行报告",text))
                                                message="运行报告已复制"
                                            }
                                        }
                                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                            LiquidAction("刷新状态",Modifier.weight(1f),enabled=!busy) { refresh++ }
                                            LiquidAction("重启系统界面",Modifier.weight(1f),enabled=!busy,selected=true) { if(dirty) save(true) else restart=true }
                                        }
                                    }
                                    SettingsGroup {
                                        UiText("当前配置",17,bold=true)
                                        Summary("下拉背景模糊",if(draft.panel.blur) "${draft.panel.radius} px" else "系统默认")
                                        Summary("下拉压暗 / 混色",if(draft.panel.tint) "${alphaPercent(draft.panel.color)}%" else "系统默认")
                                        Summary("通知左右边距",if(draft.margins) "${draft.margin} dp" else "系统默认")
                                        Summary("流体云",if(draft.cloud) "PUI + C17" else "系统默认")
                                        LiquidAction("前往调节",Modifier.fillMaxWidth(),selected=true) { page=1 }
                                    }
                                    UiText("首次使用：在 LSPosed 启用模块，作用域选择系统界面，重启手机。",12,muted=true)
                                }
                                1 -> {
                                    ChoiceTabs(listOf("下拉背景","通知横幅","布局与流体云"),section) { section=it }
                                    when(section) {
                                        0,1 -> {
                                            val area=if(section==0) draft.panel else draft.banner
                                            fun change(a: Area) { draft=if(section==0) draft.copy(panel=a) else draft.copy(banner=a) }
                                            UiText(if(section==0) "控制中心与通知中心共用" else "悬浮与全屏通知横幅",13,muted=true)
                                            SettingsGroup {
                                                LiquidSwitchRow("自定义背景模糊","关闭后使用 C17 原有模糊",area.blur) { change(area.copy(blur=it)) }
                                                if(area.blur) LiquidValueSlider("模糊半径",area.radius,0..200," px") { change(area.copy(radius=it)) }
                                            }
                                            SettingsGroup {
                                                LiquidSwitchRow(if(section==0) "调节原有压暗 / 混色" else "自定义横幅混色",
                                                    "关闭后恢复系统原有混色",area.tint) { change(area.copy(tint=it)) }
                                                if(area.tint) {
                                                    LiquidValueSlider(if(section==0) "压暗 / 混色程度" else "混色程度",alphaPercent(area.color),0..100,"%") {
                                                        change(area.copy(color=(area.color and 0xffffff) or ((it*255/100) shl 24)))
                                                    }
                                                    LiquidAction("混色颜色  ${hex(area.color)}",Modifier.fillMaxWidth()) { picker=if(section==0) 0 else 1 }
                                                    UiText("黑色用于压暗；透明度为 0% 时无混色。选择其他颜色可改变遮罩色调。",12,muted=true)
                                                }
                                            }
                                            LiquidAction("恢复此区域系统参数",Modifier.fillMaxWidth()) { change(Area(color=area.color)) }
                                            UiText("圆角沿用 C17。模糊半径与混色在系统的实际背景路径应用。",12,muted=true)
                                        }
                                        else -> {
                                            SettingsGroup {
                                                UiText("通知布局",18,bold=true)
                                                LiquidSwitchRow("自定义左右边距","适配单栏、双栏与分屏通知列表",draft.margins) { draft=draft.copy(margins=it) }
                                                if(draft.margins) LiquidValueSlider("左右各留白",draft.margin,0..64," dp") { draft=draft.copy(margin=it) }
                                                UiText("默认参考值为 30 dp。关闭后使用系统默认边距。",12,muted=true)
                                            }
                                            SettingsGroup {
                                                UiText("流体云",18,bold=true)
                                                LiquidSwitchRow("融合 PUI 流体云","重复资源保留 C17 版本",draft.cloud) { draft=draft.copy(cloud=it) }
                                                Summary("胶囊最大高度","34 dp")
                                                Summary("深色文字强调色","#FFFFA800")
                                                UiText("包含 C17 胶囊边框、背景、卡片混色与渐变配色。需 SystemUI 加载流体云插件后生效。",12,muted=true)
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    UiText("配置管理",13,muted=true)
                                    SettingsGroup {
                                        UiText("带走你的设置",18,bold=true)
                                        UiText("导出当前界面中的全部参数，包含尚未保存的修改。导入后可预览，再保存应用。",12,muted=true)
                                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                            LiquidAction("导入配置",Modifier.weight(1f),enabled=!busy) { importLauncher.launch(arrayOf("application/json","text/plain","application/octet-stream")) }
                                            LiquidAction("导出配置",Modifier.weight(1f),enabled=!busy) { exportText=draft.json();exportLauncher.launch("thunder-${SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(Date())}.json") }
                                        }
                                    }
                                    UiText("界面外观",13,muted=true)
                                    SettingsGroup {
                                        UiText("明暗模式",16,bold=true)
                                        ChoiceTabs(listOf("跟随系统","浅色","深色"),draft.theme) { draft=draft.copy(theme=it) }
                                        LiquidAction("强调色  ${hex(draft.accent)}",Modifier.fillMaxWidth()) { picker=2 }
                                        LiquidAction("背景底色  ${hex(draft.backdropColor)}",Modifier.fillMaxWidth()) { picker=3 }
                                        LiquidValueSlider("玻璃雾度",draft.glassStrength,0..100,"%") { draft=draft.copy(glassStrength=it) }
                                        LiquidValueSlider("玻璃折射",draft.refraction,0..100,"%") { draft=draft.copy(refraction=it) }
                                        LiquidValueSlider("界面圆角",draft.roundness,16..36," dp") { draft=draft.copy(roundness=it) }
                                    }
                                    LiquidAction("恢复全部默认设置",Modifier.fillMaxWidth(),enabled=!busy) { reset=true }
                                    UiText("thunder / ${BuildConfig.VERSION_NAME}\nLSPosed API 102 · Miuix 0.9.0\nLiquid Glass · Kyant0 Backdrop 1.0.6\n界面配色仅影响此应用，系统背景在调节页修改。",12,muted=true)
                                }
                            }
                            if(message.isNotEmpty() && page!=0) SettingsGroup { UiText(message,13);LiquidAction("收起提示") { message="" } }
                        }
                    }
                    ProgressiveHeader(overlaySource,Modifier.fillMaxWidth().height(108.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()).align(Alignment.TopCenter))
                    Row(Modifier.align(Alignment.TopCenter).widthIn(max=640.dp).fillMaxWidth().statusBarsPadding().padding(horizontal=24.dp,vertical=14.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            UiText(listOf("主页","调节","设置")[page],28,bold=true)
                            UiText(if(dirty) "有未保存的修改" else "独立 LSPosed 模块 · ColorOS 17",11,muted=true)
                        }
                        LiquidAction(if(busy) "处理中" else if(dirty) "保存" else "已保存",enabled=!busy&&dirty,selected=dirty) { save() }
                    }
                    CompositionLocalProvider(LocalGlass provides style.copy(source=overlaySource)) {
                        FloatingNav(page,{ page=it },Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=28.dp,vertical=14.dp))
                    }
                    if(picker>=0) ColorDialog(when(picker) {0->draft.panel.color;1->draft.banner.color;2->draft.accent;else->draft.backdropColor},picker>=2,{picker=-1}) { color ->
                        draft=when(picker) {0->draft.copy(panel=draft.panel.copy(color=color));1->draft.copy(banner=draft.banner.copy(color=color));2->draft.copy(accent=color);else->draft.copy(backdropColor=color)};picker=-1
                    }
                    if(restart) SettingsDialog("重启系统界面",{restart=false}) {
                        UiText("状态栏、控制中心与通知会短暂消失，然后恢复。需要授予此应用 Root 权限。",14,muted=true)
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            LiquidAction("取消",Modifier.weight(1f)) { restart=false }
                            LiquidAction("重启",Modifier.weight(1f),selected=true) { restart=false;busy=true;scope.launch { message=SystemUiRestarter.restart(this@MainActivity) { message=it };busy=false;refresh++ } }
                        }
                    }
                    if(reset) SettingsDialog("恢复默认设置",{reset=false}) {
                        UiText("将界面外观和效果参数恢复为默认值，保存后才会替换已存配置。",14,muted=true)
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            LiquidAction("取消",Modifier.weight(1f)) { reset=false }
                            LiquidAction("恢复",Modifier.weight(1f),selected=true) { draft=Settings();reset=false }
                        }
                    }
                    if(importConfirm) SettingsDialog("导入此配置？",{importConfirm=false}) {
                        UiText("会替换当前未保存的修改。导入后可检查参数，再点击保存。",14,muted=true)
                        Summary("下拉模糊",if(imported.panel.blur) "${imported.panel.radius} px" else "系统默认")
                        Summary("压暗 / 混色",if(imported.panel.tint) "${alphaPercent(imported.panel.color)}%" else "系统默认")
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            LiquidAction("取消",Modifier.weight(1f)) { importConfirm=false }
                            LiquidAction("导入",Modifier.weight(1f),selected=true) { draft=imported;importConfirm=false;message="已导入，检查后点击保存" }
                        }
                    }
                }
            }
        }
        }
    }
    private fun runtimeStatus(): Pair<String,String> {
        val r=createDeviceProtectedStorageContext().getSharedPreferences("runtime",0)
        val time=r.getLong("time",0)
        val hookVersion=r.getInt("hook_version",0)
        val reportElapsed=r.getLong("elapsed",-1)
        if(reportElapsed>android.os.SystemClock.elapsedRealtime()) return "等待本次开机报告" to "当前记录来自上次开机，请重启系统界面并刷新。"
        if(time==0L) return "等待系统界面报告" to "${RemoteSettings.status}\n启用 LSPosed 模块并重启后，下拉通知或触发横幅，再刷新查看。"
        val error=r.getString("error","").orEmpty()
        val pending=r.getLong("revision",-1)!=SettingsProvider.preferences(this).getLong("revision",0)
        val title=when { hookVersion!=BuildConfig.VERSION_CODE->"等待本版 Hook 启动"; error.isNotEmpty()->"部分接口需要检查";pending->"等待重启应用设置";else->"已收到系统界面报告" }
        val hits=r.getString("hits","").orEmpty().ifEmpty { "尚未触发" }
        return title to "${RemoteSettings.status}\nSystemUI PID ${r.getInt("pid",-1)} · Hook $hookVersion · 配置 ${r.getLong("revision",-1)}\n最近报告 ${SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(Date(time))}\n命中：$hits\n实际参数：${r.getString("effective", "尚未触发绘制")}"+(if(error.isNotEmpty()) "\n异常：$error" else "")
    }

}

private fun alphaPercent(c: Int)=((c ushr 24)*100/255f).toInt()
@Composable private fun Summary(title: String,value: String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        UiText(title,13,muted=true,modifier=Modifier.weight(1f).padding(end=12.dp));UiText(value,13,bold=true)
    }
}
@Composable private fun Wallpaper(modifier: Modifier,s: GlassStyle) {
    Box(modifier.background(if(s.dark) MiuixTheme.colorScheme.surface else Color(s.settings.backdropColor)))
}
