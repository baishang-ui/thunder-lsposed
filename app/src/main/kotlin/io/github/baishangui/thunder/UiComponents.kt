package io.github.baishangui.thunder

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.math.roundToInt

internal data class GlassStyle(val source: Backdrop, val dark: Boolean, val settings: Settings, val colors: Colors) {
    val ink get() = colors.onSurface
    val muted get() = colors.onSurfaceVariantSummary
    val accent get() = colors.primary
    val surface get() = colors.surface
    val background get() = if (settings.colorMode == 0 && !dark) Color(settings.backdropColor) else colors.background
}
internal val LocalGlass = staticCompositionLocalOf<GlassStyle> { error("Missing UI style") }

// The original Catalog components use these values in place of their hardcoded theme.
data class LiquidAppearance(val dark: Boolean, val accent: Color, val blur: Float = 1f, val refraction: Float = 1f)
val LocalLiquidAppearance = staticCompositionLocalOf { LiquidAppearance(false, Color(0xff3482ff)) }

@Composable internal fun UiText(text: String, size: Int = 15, muted: Boolean = false,
    bold: Boolean = false, modifier: Modifier = Modifier, color: Color? = null,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip) {
    val s = LocalGlass.current
    Text(text, modifier = modifier, color = color ?: if (muted) s.muted else s.ink,
        fontSize = size.sp, lineHeight = (size * 1.45f).sp, maxLines = maxLines, overflow = overflow,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
}

@Composable internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), cornerRadius = LocalGlass.current.settings.roundness.dp, insideMargin = PaddingValues(0.dp)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable internal fun LiquidAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    selected: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    val s = LocalGlass.current
    val contentColor = if (selected) s.colors.onPrimary else s.ink
    // Remove gesture handlers altogether while disabled, including the author's press animation.
    Box(modifier.alpha(if (enabled) 1f else .45f).semantics(mergeDescendants = icon != null) {
        if (icon != null) contentDescription = text
        if (!enabled) disabled()
    }, contentAlignment = Alignment.Center, propagateMinConstraints = icon == null) {
        LiquidButton(onClick = { if (enabled) onClick() }, backdrop = s.source,
            modifier = (if (icon != null) Modifier.size(48.dp) else Modifier.heightIn(min = 48.dp))
                .then(if (!enabled) Modifier.clearAndSetSemantics { disabled() } else Modifier),
            isInteractive = enabled, tint = if (selected) s.accent else Color.Unspecified,
            surfaceColor = if (selected) Color.Unspecified else s.surface.copy(alpha = .18f),
            contentPadding = PaddingValues(horizontal = if (icon != null) 8.dp else 16.dp)) {
            if (icon != null) Icon(icon, contentDescription = null,
                modifier = Modifier.size(22.dp), tint = contentColor)
            else UiText(text, 14, bold = true, color = contentColor)
        }
    }
}

@Composable internal fun SettingsMenuGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UiText(title, 13, muted = true, modifier = Modifier.padding(start = 8.dp))
        Card(Modifier.fillMaxWidth(), cornerRadius = LocalGlass.current.settings.roundness.dp, insideMargin = PaddingValues(0.dp)) {
            Column(content = content)
        }
    }
}

@Composable internal fun SettingsMenuItem(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    val s = LocalGlass.current
    BasicComponent(
        title = title,
        summary = summary.takeIf { it.isNotBlank() },
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 13.dp),
        startAction = {
            Box(Modifier.padding(end = 14.dp).size(36.dp)
                .background(s.colors.secondaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = s.colors.onSecondaryContainer)
            }
        },
        endActions = {
            Icon(MiuixIcons.Regular.ChevronForward, contentDescription = null,
                modifier = Modifier.padding(start = 12.dp).size(18.dp), tint = s.muted)
        },
        onClick = onClick
    )
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
        Row(Modifier.weight(1f).heightIn(min = 52.dp).clickable(onClickLabel = "选择$title") { open = true },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp).background(LocalGlass.current.colors.secondaryContainer, CircleShape)
                .background(Color(color), CircleShape).border(1.dp, LocalGlass.current.colors.outline.copy(alpha = .3f), CircleShape))
            UiText(title, modifier = Modifier.weight(1f))
            UiText(if (opaque) "#%06X".format(color and 0xffffff) else hex(color), 12, muted = true)
            Icon(MiuixIcons.Regular.ChevronForward, null, Modifier.size(16.dp), tint = LocalGlass.current.muted)
        }
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
        Column(Modifier.weight(1f).heightIn(min = 48.dp)
            .clickable(onClickLabel = title) { latestChange.value(!latestChecked.value) }
            .wrapContentHeight().padding(end = 12.dp)) {
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
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
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
        val icons = listOf(MiuixIcons.Regular.GridView, MiuixIcons.Regular.Tune, MiuixIcons.Regular.Settings)
        listOf("主页", "调节", "设置").forEachIndexed { i, name ->
            LiquidBottomTab(onClick = { latestChange.value(i) }, modifier = Modifier.semantics { selected = page == i }) {
                Icon(icons[i], contentDescription = null, modifier = Modifier.size(22.dp), tint = if (page == i) s.accent else s.muted)
                UiText(name, 12, bold = page == i, color = if (page == i) s.ink else s.muted)
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
        ColorPicker(color = Color(color), onColorChanged = { selected ->
            set(if (opaque) selected.copy(alpha = 1f).toArgb() else selected.toArgb())
        }, modifier = Modifier.fillMaxWidth())
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
