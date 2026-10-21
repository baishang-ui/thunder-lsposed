package io.github.baishangui.thunder

import android.graphics.RenderEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.HsvHueSlider
import top.yukonga.miuix.kmp.basic.HsvSaturationSlider
import top.yukonga.miuix.kmp.basic.HsvValueSlider
import top.yukonga.miuix.kmp.basic.HsvAlphaSlider
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.drawCheckerboard
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.clip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape
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
internal val LocalInlineSettings = staticCompositionLocalOf { false }
internal val LocalGlass = staticCompositionLocalOf<GlassStyle> { error("Missing UI style") }

// Kyant0's official Progressive Blur tutorial; keep the original header tint.
// https://kyant.gitbook.io/backdrop/tutorials/progressive-blur
private const val HEADER_ALPHA_MASK = """
uniform shader content;
uniform float2 size;
layout(color) uniform half4 tint;
uniform float tintIntensity;
half4 main(float2 coord) {
    float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
    float tintAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
    return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
}
"""

@Composable internal fun ProgressiveHeader(source: Backdrop, modifier: Modifier) {
    val style = LocalGlass.current
    Box(modifier.drawPlainBackdrop(source, shape = { RectangleShape }, effects = {
        blur(4f.dp.toPx())
        effect(RenderEffect.createRuntimeShaderEffect(
            obtainRuntimeShader("AlphaMask", HEADER_ALPHA_MASK).apply {
                setFloatUniform("size", size.width, size.height)
                setColorUniform("tint", style.surface.toArgb())
                setFloatUniform("tintIntensity", if (style.dark) .55f else .65f)
            }, "content"
        ))
    }))
}

// The original Catalog components use these values in place of their hardcoded theme.
data class LiquidAppearance(val dark: Boolean, val accent: Color, val blur: Float = 1f, val refraction: Float = 1f)
val LocalLiquidAppearance = staticCompositionLocalOf { LiquidAppearance(false, Color(0xff3482ff)) }

@Composable internal fun UiText(text: String, size: Int = 15, muted: Boolean = false,
    bold: Boolean = false, modifier: Modifier = Modifier, color: Color? = null,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null) {
    val s = LocalGlass.current
    Text(text, modifier = modifier, color = color ?: if (muted) s.muted else s.ink,
        fontSize = size.sp, lineHeight = (size * 1.45f).sp, maxLines = maxLines, overflow = overflow,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, textAlign = textAlign)
}

@Composable internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), cornerRadius = LocalGlass.current.settings.roundness.dp,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable internal fun SettingsDivider() {
    HorizontalDivider(color = LocalGlass.current.colors.dividerLine)
}

@Composable internal fun SettingsSectionTitle(title: String) {
    UiText(title, 13, muted = true, bold = true,
        modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() })
}

@Composable internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle(title)
        if (LocalInlineSettings.current) Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        else SettingsGroup(content = content)
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
            surfaceColor = if (selected) s.accent else s.surface.copy(alpha = .18f),
            shape = if (icon != null) Capsule() else RoundedRectangle(24.dp),
            contentPadding = if (icon != null) PaddingValues(8.dp)
                else PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
            if (icon != null) Icon(icon, contentDescription = null,
                modifier = Modifier.size(22.dp), tint = contentColor)
            else UiText(text, 14, bold = true, color = contentColor)
        }
    }
}

@Composable internal fun SettingsMenuGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle(title)
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
            Surface(Modifier.padding(end = 14.dp).size(36.dp), shape = miuixShape(12.dp),
                color = s.colors.secondaryContainer, contentColor = s.colors.onSecondaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = s.colors.onSecondaryContainer)
                }
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
@Composable internal fun UndoFooter(title: String, restore: (() -> Unit)?) {
    if (restore != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        LiquidAction("回退", modifier = Modifier.semantics { contentDescription = "回退${title}的修改" },
            onClick = restore)
    }
}
@Composable internal fun LiquidColorSetting(title: String, color: Int, opaque: Boolean = false, change: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val history = LocalControlHistory.current
    val key = LocalControlScope.current + ":color:" + title
    SideEffect { history.register(key, color) }
    val original = history.original(key, color)
    Column(Modifier.fillMaxWidth()) {
        BasicComponent(
            modifier = Modifier.clip(miuixShape(12.dp)).semantics { role = Role.Button },
            insideMargin = PaddingValues(8.dp),
            onClick = { open = true },
            startAction = {
                Surface(Modifier.size(32.dp).clip(CircleShape).drawCheckerboard(), shape = CircleShape,
                    color = Color(color), border = androidx.compose.foundation.BorderStroke(
                        1.dp, LocalGlass.current.colors.outline.copy(alpha = .3f))) {}
            },
            endActions = {
                Icon(MiuixIcons.Regular.ChevronForward, null, Modifier.size(16.dp), tint = LocalGlass.current.muted)
            }
        ) {
            UiText(title)
            UiText(if (opaque) "#%06X".format(color and 0xffffff)
                else "${hex(color)} · 不透明度 ${TintColor.percent(color)}%", 12, muted = true)
        }
        UndoFooter(title, original?.let { { change(it) } })
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
    Column(Modifier.fillMaxWidth()) {
        BasicComponent(
            modifier = Modifier.clip(miuixShape(12.dp)).semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
            },
            insideMargin = PaddingValues(8.dp),
            onClick = { latestChange.value(!latestChecked.value) },
            endActions = {
                Box(Modifier.size(68.dp, 48.dp), contentAlignment = Alignment.Center) {
                    // The complete preference is the accessible switch; this retains the glass drag interaction.
                    LiquidToggle(selected = { latestChecked.value }, onSelect = { latestChange.value(it) },
                        backdrop = s.source, modifier = Modifier.clearAndSetSemantics {})
                }
            }
        ) {
            UiText(title, bold = true)
            if (subtitle.isNotEmpty()) UiText(subtitle, 12, muted = true, modifier = Modifier.padding(top = 4.dp))
        }
        UndoFooter(title, original?.let { { change(it) } })
    }
}

@Composable internal fun LiquidValueSlider(title: String, value: Int, range: IntRange, unit: String = "", change: (Int) -> Unit) {
    val s = LocalGlass.current
    var dragging by remember { mutableFloatStateOf(value.toFloat()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(value) { dragging = value.toFloat() }
    val history = LocalControlHistory.current
    val id = LocalControlScope.current + ":slider:" + title
    SideEffect { history.register(id, value) }
    val original = history.original(id, value)
    val latestCommit = rememberUpdatedState<(Int) -> Unit> { next ->
        history.record(id, value, next); if (next != value) change(next)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InputPreference(title, "${dragging.roundToInt()}$unit", inlineValue = true) { editing = true }
        key(range) {
            LiquidSlider(value = { dragging }, onValueChange = { dragging = it.coerceIn(range.first.toFloat(), range.last.toFloat()) },
                onValueChangeFinished = { latestCommit.value(dragging.roundToInt().coerceIn(range)) },
                valueRange = range.first.toFloat()..range.last.toFloat(), visibilityThreshold = .01f,
                backdrop = s.source, modifier = Modifier.height(48.dp).semantics(mergeDescendants = true) {
                    contentDescription = title
                    stateDescription = "${dragging.roundToInt()}$unit"
                    progressBarRangeInfo = ProgressBarRangeInfo(dragging, range.first.toFloat()..range.last.toFloat(), range.last-range.first-1)
                    setProgress { dragging = it.coerceIn(range.first.toFloat(),range.last.toFloat()); latestCommit.value(dragging.roundToInt()); true }
                }.focusable())
        }
        UndoFooter(title, original?.let { { change(it) } })
    }
    if (editing) NumberInputDialog(title, dragging.roundToInt(), range, unit, dismiss = { editing = false }) {
        dragging = it.toFloat()
        latestCommit.value(it)
        editing = false
    }
}

@Composable internal fun ChoiceTabs(labels: List<String>, selected: Int, undoable: Boolean = true, select: (Int) -> Unit) {
    val history = LocalControlHistory.current
    val id = LocalControlScope.current + ":choice:" + labels.joinToString()
    SideEffect { if(undoable) history.register(id, selected) }
    val original = if(undoable) history.original(id, selected) else null
    val textMeasurer = rememberTextMeasurer()
    val style = MiuixTheme.textStyles.main.copy(
        fontSize = MiuixTheme.textStyles.body2.fontSize, fontWeight = FontWeight.Bold)
    val labelSizes = labels.map { textMeasurer.measure(it, style, maxLines = 1).size }
    val density = LocalDensity.current
    val minWidth = with(density) { labelSizes.maxOf { it.width }.toDp() } + 24.dp
    val height = maxOf(TabRowDefaults.TabRowWithContourHeight,
        with(density) { labelSizes.maxOf { it.height }.toDp() } + 26.dp)
    val selectChoice: (Int) -> Unit = {
        if (undoable) history.record(id, selected, it)
        select(it)
    }
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Prefer complete options to a clipped horizontal strip on narrow screens or with large text.
            val requiredWidth = minWidth * labels.size + 5.dp * (labels.size - 1) + 10.dp
            if (requiredWidth <= maxWidth) {
                TabRowWithContour(tabs = labels, selectedTabIndex = selected, onTabSelected = selectChoice,
                    minWidth = minWidth, maxWidth = maxOf(minWidth, 180.dp), height = height)
            } else {
                Column(Modifier.fillMaxWidth().semantics { selectableGroup() }) {
                    labels.forEachIndexed { index, label ->
                        BasicComponent(
                            modifier = Modifier.clip(miuixShape(12.dp)).semantics(mergeDescendants = true) {
                                role = Role.RadioButton
                                this.selected = selected == index
                            },
                            insideMargin = PaddingValues(8.dp),
                            onClick = { selectChoice(index) },
                            endActions = {
                                RadioButton(selected = selected == index, onClick = null,
                                    modifier = Modifier.clearAndSetSemantics {})
                            }
                        ) { UiText(label) }
                    }
                }
            }
        }
        UndoFooter(labels.joinToString(" / "), original?.let { { select(it) } })
    }
}

@Composable internal fun floatingNavHeight() = with(LocalDensity.current) {
    maxOf(64.dp, 22.dp + 2.dp + (12 * 1.45f).sp.toDp() + 16.dp)
}

@Composable internal fun FloatingNav(page: Int, change: (Int) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalGlass.current
    val latestPage = rememberUpdatedState(page)
    val latestChange = rememberUpdatedState(change)
    LiquidBottomTabs(selectedTabIndex = { latestPage.value }, onTabSelected = { latestChange.value(it) },
        backdrop = s.source, tabsCount = 2, height = floatingNavHeight(),
        modifier = modifier.widthIn(max = 320.dp).fillMaxWidth().semantics { selectableGroup() }) {
        val icons = listOf(MiuixIcons.Regular.Tune, MiuixIcons.Regular.Settings)
        listOf("调节", "设置").forEachIndexed { i, name ->
            LiquidBottomTab(onClick = { latestChange.value(i) }, modifier = Modifier.semantics { selected = page == i }) {
                Icon(icons[i], contentDescription = null, modifier = Modifier.size(22.dp), tint = if (page == i) s.accent else s.muted)
                UiText(name, 12, bold = page == i, color = if (page == i) s.ink else s.muted)
            }
        }
    }
}

@Composable internal fun SettingsDialog(title: String, dismiss: () -> Unit,
    footer: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val parent = LocalGlass.current
    WindowDialog(show = true, title = title, backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
        onDismissRequest = dismiss) {
        val backdrop = rememberLayerBackdrop()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(MiuixTheme.colorScheme.surfaceContainer))
            CompositionLocalProvider(LocalGlass provides parent.copy(source = backdrop)) {
                val screenLimit = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * .7f).toDp() }
                Column(Modifier.fillMaxWidth().heightIn(max = minOf(maxHeight, screenLimit))) {
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
                    if (footer != null) {
                        Spacer(Modifier.height(16.dp))
                        footer()
                    }
                }
            }
        }
    }
}

internal fun hex(color: Int) = "#%08X".format(color)

@Composable internal fun ColorDialog(initial: Int, opaque: Boolean = false, dismiss: () -> Unit, confirm: (Int) -> Unit) {
    var selection by rememberSaveable(stateSaver = PickerColor.saver) {
        mutableStateOf(PickerColor.from(if (opaque) initial or 0xff000000.toInt() else initial))
    }
    fun formatted(color: Int) = if (opaque) "#%06X".format(color and 0xffffff) else hex(color)
    var input by rememberSaveable { mutableStateOf(formatted(initial)) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableIntStateOf(if (opaque) 1 else 0) }
    fun update(next: PickerColor) { selection = next; input = formatted(next.argb); invalid = false }
    SettingsDialog("选择颜色", dismiss, footer = {
        val keyboard = LocalSoftwareKeyboardController.current
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidAction("取消", Modifier.weight(1f)) { keyboard?.hide(); dismiss() }
            LiquidAction("确定", Modifier.weight(1f), enabled = !invalid, selected = true) {
                keyboard?.hide()
                confirm(parsePickerColor(input, opaque) ?: selection.argb)
            }
        }
    }) {
        val keyboard = LocalSoftwareKeyboardController.current
        Surface(Modifier.fillMaxWidth().height(40.dp).clip(miuixShape(12.dp)).drawCheckerboard(),
            shape = miuixShape(12.dp), color = Color(selection.argb)) {}
        // MIUIx 0.9.0 ColorPalette always includes alpha. Opaque colors use HSV/HEX instead.
        ChoiceTabs(if (opaque) listOf("调色", "HEX") else listOf("色板", "调色", "HEX"),
            if (opaque) mode - 1 else mode, undoable = false) {
            keyboard?.hide()
            update(selection)
            mode = if (opaque) it + 1 else it
        }
        if (mode == 0) ColorPalette(color = Color(selection.argb),
            onColorChanged = { update(PickerColor.from(it.toArgb(), selection)) },
            modifier = Modifier.fillMaxWidth(), showPreview = false)
        if (mode == 1) {
            ColorChannel("色相", "${selection.hue.roundToInt()}°", selection.hue / 360f,
                { update(selection.copy(hue = it * 360f)) }) {
                HsvHueSlider(selection.hue, { update(selection.copy(hue = it * 360f)) })
            }
            ColorChannel("饱和度", "${(selection.saturation * 100).roundToInt()}%", selection.saturation,
                { update(selection.copy(saturation = it)) }) {
                HsvSaturationSlider(selection.hue, selection.saturation, { update(selection.copy(saturation = it)) })
            }
            ColorChannel("亮度", "${(selection.brightness * 100).roundToInt()}%", selection.brightness,
                { update(selection.copy(brightness = it)) }) {
                HsvValueSlider(selection.hue, selection.saturation, selection.brightness, { update(selection.copy(brightness = it)) })
            }
        }
        if (!opaque && mode == 1) ColorChannel("不透明度", "${(selection.alpha * 100).roundToInt()}%", selection.alpha,
            { update(selection.copy(alpha = it)) }) {
            HsvAlphaSlider(selection.hue, selection.saturation, selection.brightness, selection.alpha,
                { update(selection.copy(alpha = it)) })
        }
        if (mode == 2) {
            val inputError = if (opaque) "请输入 6 位 RGB 或 FF 开头的 8 位 ARGB" else "请输入 6 位 RGB 或 8 位 ARGB"
            TextField(value = input, onValueChange = { text ->
                input = text
                val parsed = parsePickerColor(text, opaque)
                invalid = parsed == null
                if (parsed != null) selection = PickerColor.from(parsed, selection)
            }, modifier = Modifier.fillMaxWidth().semantics { if (invalid) error(inputError) },
                label = if (opaque) "HEX · RRGGBB" else "HEX · AARRGGBB / RRGGBB",
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
            if (invalid) UiText(inputError, 12, color = MiuixTheme.colorScheme.error)
            else UiText(if (opaque) "输入 RGB 颜色，例如 #3482FF。"
                else "8 位格式为 AARRGGBB，前两位表示不透明度；6 位 RGB 使用完全不透明色。", 12, muted = true)
        }
    }
}

@Composable private fun ColorChannel(title: String, value: String, progress: Float, change: (Float) -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            UiText(title, 13)
            UiText(value, 12, muted = true)
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) {
            contentDescription = title
            stateDescription = value
            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            setProgress { change(it.coerceIn(0f, 1f)); true }
        }.focusable(), contentAlignment = Alignment.Center) { content() }
    }
}
