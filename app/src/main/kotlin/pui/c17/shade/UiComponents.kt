package pui.c17.shade

import android.graphics.RenderEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.catalog.components.*
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.math.roundToInt

internal data class GlassStyle(val source: Backdrop, val dark: Boolean, val settings: Settings) {
    val ink get() = if (dark) Color(0xfff2f2f2) else Color(0xff181818)
    val muted get() = if (dark) Color(0xffaaaaaa) else Color(0xff737373)
    val accent get() = Color(settings.accent)
    val surface get() = if (dark) Color(0xff242424) else Color.White
}
internal val LocalGlass = staticCompositionLocalOf<GlassStyle> { error("Missing UI style") }

// The original Catalog components use these values in place of their hardcoded theme.
data class LiquidAppearance(val dark: Boolean, val accent: Color, val blur: Float = 1f, val refraction: Float = 1f)
val LocalLiquidAppearance = staticCompositionLocalOf { LiquidAppearance(false, Color(0xff3482ff)) }

@Composable internal fun UiText(text: String, size: Int = 15, muted: Boolean = false,
    bold: Boolean = false, modifier: Modifier = Modifier, color: Color? = null) {
    val s = LocalGlass.current
    Text(text, modifier = modifier, color = color ?: if (muted) s.muted else s.ink,
        fontSize = size.sp, lineHeight = (size * 1.45f).sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
}

@Composable internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), cornerRadius = LocalGlass.current.settings.roundness.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
    }
}

@Composable internal fun LiquidAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    selected: Boolean = false, onClick: () -> Unit) {
    val s = LocalGlass.current
    // Remove gesture handlers altogether while disabled, including the author's press animation.
    Box(modifier.alpha(if (enabled) 1f else .45f).semantics { if (!enabled) disabled() }, propagateMinConstraints = true) {
        LiquidButton(onClick = { if (enabled) onClick() }, backdrop = s.source,
            modifier = Modifier.then(if (!enabled) Modifier.clearAndSetSemantics { disabled() } else Modifier),
            isInteractive = enabled, tint = if (selected) s.accent else Color.Unspecified,
            surfaceColor = if (selected) Color.Unspecified else s.surface.copy(alpha = .18f)) {
            UiText(text, 14, bold = true, color = if (selected) (if (s.accent.luminance() > .45f) Color.Black else Color.White) else s.ink)
        }
    }
}

internal class ControlHistory {
    private val originals = mutableStateMapOf<String, Any>()
    fun <T : Any> register(key: String, value: T) { if (!originals.containsKey(key)) originals[key] = value }
    fun <T : Any> record(key: String, before: T, after: T) {
        if (before != after && !originals.containsKey(key)) originals[key] = before
    }
    @Suppress("UNCHECKED_CAST") fun <T : Any> original(key: String, value: T): T? = originals[key]?.takeIf { it != value } as T?
}
internal val LocalControlHistory = staticCompositionLocalOf { ControlHistory() }
internal val LocalControlScope = staticCompositionLocalOf { "" }
@Composable private fun UndoAction(restore: (() -> Unit)?) {
    if (restore != null) UiText("回退", 12, color = LocalGlass.current.accent,
        modifier = Modifier.heightIn(min = 48.dp).clickable(onClickLabel = "回退此项修改", onClick = restore).wrapContentHeight().padding(horizontal = 8.dp))
}
@Composable internal fun LiquidColorSetting(title: String, color: Int, opaque: Boolean = false, change: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val history = LocalControlHistory.current
    val key = LocalControlScope.current + ":color:" + title
    SideEffect { history.register(key, color) }
    val original = history.original(key, color)
    Row(verticalAlignment = Alignment.CenterVertically) {
        LiquidAction("$title  ${hex(color)}", Modifier.weight(1f)) { open = true }
        UndoAction(original?.let { { change(it) } })
    }
    if (open) CompositionLocalProvider(LocalControlScope provides "$key:dialog") {
        ColorDialog(color, opaque, { open = false }) {
            history.record(key, color, it); change(it); open = false
        }
    }
}

@Composable internal fun LiquidSwitchRow(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) {
    val s = LocalGlass.current
    val latestChecked = rememberUpdatedState(checked)
    val history = LocalControlHistory.current
    val key = LocalControlScope.current + ":switch:" + title
    SideEffect { history.register(key, checked) }
    val original = history.original(key, checked)
    val latestChange = rememberUpdatedState<(Boolean) -> Unit> { history.record(key, checked, it); change(it) }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            UiText(title, bold = true)
            if (subtitle.isNotEmpty()) UiText(subtitle, 12, muted = true, modifier = Modifier.padding(top = 4.dp))
        }
        UndoAction(original?.let { { change(it) } })
        Box(Modifier.size(68.dp, 48.dp), contentAlignment = Alignment.Center) {
            LiquidToggle(selected = { latestChecked.value }, onSelect = { latestChange.value(it) },
                backdrop = s.source, modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = title; toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
                    role = Role.Switch
                    onClick { latestChange.value(!latestChecked.value); true }
                })
        }
    }
}

@Composable internal fun LiquidValueSlider(title: String, value: Int, range: IntRange, unit: String = "", change: (Int) -> Unit) {
    val s = LocalGlass.current
    var dragging by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) { dragging = value.toFloat() }
    val history = LocalControlHistory.current
    val id = LocalControlScope.current + ":slider:" + title
    SideEffect { history.register(id, value) }
    val original = history.original(id, value)
    val latestCommit = rememberUpdatedState<(Int) -> Unit> { next ->
        history.record(id, value, next); if (next != value) change(next)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            UiText(title, 14, modifier = Modifier.weight(1f))
            UndoAction(original?.let { { change(it) } })
            UiText("${dragging.roundToInt()}$unit", 14, bold = true)
        }
        key(range) {
            LiquidSlider(value = { dragging }, onValueChange = { dragging = it.coerceIn(range.first.toFloat(), range.last.toFloat()) },
                onValueChangeFinished = { latestCommit.value(dragging.roundToInt().coerceIn(range)) },
                valueRange = range.first.toFloat()..range.last.toFloat(), visibilityThreshold = .01f,
                backdrop = s.source, modifier = Modifier.height(48.dp).semantics(mergeDescendants = true) {
                    contentDescription = title
                    progressBarRangeInfo = ProgressBarRangeInfo(dragging, range.first.toFloat()..range.last.toFloat(), range.last-range.first-1)
                    setProgress { dragging = it.coerceIn(range.first.toFloat(),range.last.toFloat()); latestCommit.value(dragging.roundToInt()); true }
                }.focusable())
        }
    }
}

@Composable internal fun ChoiceTabs(labels: List<String>, selected: Int, undoable: Boolean = true, select: (Int) -> Unit) {
    val history = LocalControlHistory.current
    val id = LocalControlScope.current + ":choice:" + labels.joinToString()
    SideEffect { if(undoable) history.register(id, selected) }
    val original = if(undoable) history.original(id, selected) else null
    Column {
        TabRowWithContour(tabs = labels, selectedTabIndex = selected, onTabSelected = { if(undoable) history.record(id, selected, it); select(it) },
            minWidth = 72.dp, maxWidth = 180.dp)
        UndoAction(original?.let { { select(it) } })
    }
}

@Composable internal fun FloatingNav(page: Int, change: (Int) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalGlass.current
    val latestPage = rememberUpdatedState(page)
    val latestChange = rememberUpdatedState(change)
    LiquidBottomTabs(selectedTabIndex = { latestPage.value }, onTabSelected = { latestChange.value(it) },
        backdrop = s.source, tabsCount = 3, modifier = modifier.widthIn(max = 380.dp).fillMaxWidth()) {
        listOf("主页", "调节", "设置").forEachIndexed { i, name ->
            LiquidBottomTab(onClick = { latestChange.value(i) }, modifier = Modifier.semantics { selected = page == i }) {
                UiText(name, 16, bold = page == i)
            }
        }
    }
}

@Composable internal fun SettingsDialog(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val parent = LocalGlass.current
    WindowDialog(show = true, title = title, onDismissRequest = dismiss) {
        val backdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(MiuixTheme.colorScheme.surfaceContainer))
            CompositionLocalProvider(LocalGlass provides parent.copy(source = backdrop)) {
                Column(Modifier.fillMaxWidth().heightIn(max = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * .65f).toDp() })
                    .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}

internal fun hex(color: Int) = "#%08X".format(color)

@Composable internal fun ColorDialog(initial: Int, opaque: Boolean = false, dismiss: () -> Unit, confirm: (Int) -> Unit) {
    var color by remember { mutableIntStateOf(initial) }
    var input by remember { mutableStateOf(hex(initial)) }
    var invalid by remember { mutableStateOf(false) }
    fun set(value: Int) { color = value; input = hex(value); invalid = false }
    SettingsDialog("选择颜色", dismiss) {
        ColorPicker(color = Color(color or (0xff shl 24)), onColorChanged = { selected ->
            set((selected.toArgb() and 0xffffff) or (color and (0xff shl 24)))
        }, modifier = Modifier.fillMaxWidth())
        if (!opaque) LiquidValueSlider("混色程度", TintColor.percent(color), 0..100, "%") {
            set(TintColor.withPercent(color,it))
        }
        TextField(value = input, onValueChange = { text ->
            input = text
            val raw = text.removePrefix("#")
            val parsed = if (raw.matches(Regex("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))) raw.toLongOrNull(16) else null
            invalid = parsed == null || (opaque && raw.length == 8 && (parsed ushr 24) != 255L)
            if (!invalid && parsed != null) color = (if (raw.length == 6) parsed or 0xff000000 else parsed).toInt()
        }, modifier = Modifier.fillMaxWidth(), label = "RGB / ARGB", singleLine = true)
        if (invalid) UiText("请输入有效的 6 位 RGB 或 8 位 ARGB 颜色" + if (opaque) "，界面颜色须不透明" else "", 12, color = MiuixTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidAction("取消", Modifier.weight(1f), onClick = dismiss)
            LiquidAction("确定", Modifier.weight(1f), enabled = !invalid, selected = true) { confirm(color) }
        }
    }
}
// Kyant0 Backdrop tutorial: alpha-masked progressive blur. The mask is applied
// to the blurred backdrop itself so content below the header fades into the page.
internal const val PROGRESSIVE_SHADER = """
uniform shader content;
uniform float2 size;
layout(color) uniform half4 tint;
uniform float tintIntensity;
half4 main(float2 coord) {
    float mask = 1.0 - smoothstep(size.y * 0.5, size.y, coord.y);
    return mix(content.eval(coord) * mask, tint * mask, tintIntensity);
}
"""
@Composable internal fun ProgressiveHeader(source: Backdrop, modifier: Modifier) {
    val s = LocalGlass.current
    Box(modifier.drawPlainBackdrop(source, shape = { RectangleShape }, effects = {
        blur(4f.dp.toPx())
        effect(RenderEffect.createRuntimeShaderEffect(obtainRuntimeShader("thunder-header-alpha-mask", PROGRESSIVE_SHADER).apply {
            setFloatUniform("size",size.width,size.height)
            setColorUniform("tint",s.surface.toArgb())
            setFloatUniform("tintIntensity",if(s.dark) .55f else .65f)
        },"content"))
    }))
}
