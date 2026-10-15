package pui.c17.shade

import android.graphics.RenderEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
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

@Composable internal fun LiquidSwitchRow(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) {
    val s = LocalGlass.current
    val latestChecked = rememberUpdatedState(checked)
    val latestChange = rememberUpdatedState(change)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            UiText(title, bold = true)
            if (subtitle.isNotEmpty()) UiText(subtitle, 12, muted = true, modifier = Modifier.padding(top = 4.dp))
        }
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
    val latestValue = rememberUpdatedState(value)
    val latestChange = rememberUpdatedState(change)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            UiText(title, 14); UiText("$value$unit", 14, bold = true)
        }
        key(range) {
            LiquidSlider(value = { latestValue.value.toFloat() }, onValueChange = { latestChange.value(it.roundToInt().coerceIn(range)) },
                valueRange = range.first.toFloat()..range.last.toFloat(), visibilityThreshold = .1f,
                backdrop = s.source, modifier = Modifier.height(48.dp).semantics(mergeDescendants = true) {
                    contentDescription = title
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat(), range.last-range.first-1)
                    setProgress { latestChange.value(it.roundToInt().coerceIn(range)); true }
                }.focusable())
        }
    }
}

@Composable internal fun ChoiceTabs(labels: List<String>, selected: Int, select: (Int) -> Unit) {
    TabRowWithContour(tabs = labels, selectedTabIndex = selected, onTabSelected = select,
        minWidth = 72.dp, maxWidth = 180.dp)
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
        if (!opaque) LiquidValueSlider("混色程度", ((color ushr 24) * 100 / 255f).roundToInt(), 0..100, "%") {
            set((color and 0xffffff) or ((it * 255 / 100) shl 24))
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
// Two separable, spatially varying convolution passes. The sample radius decreases
// continuously toward the lower edge; alpha also fades to prevent a hard seam.
internal const val PROGRESSIVE_SHADER = """
uniform shader content;
uniform float height;
uniform float radius;
uniform float origin;
uniform float2 axis;
uniform float fade;
half4 main(float2 p) {
    float t = 1.0 - smoothstep(height * 0.32, height, p.y - origin);
    float r = radius * t;
    half4 c = half4(0.0);
    float total = 0.0;
    for (int i = -6; i <= 6; i++) {
        float q = float(i) / 6.0;
        float weight = exp(-q * q * 2.0);
        c += content.eval(p + axis * r * q) * weight;
        total += weight;
    }
    return c / total * mix(1.0, 1.0 - smoothstep(height * 0.65, height, p.y - origin), fade);
}
"""
@Composable internal fun ProgressiveHeader(source: Backdrop, modifier: Modifier) {
    val s = LocalGlass.current
    Box(modifier.drawPlainBackdrop(source, shape = { RectangleShape }, effects = {
        padding = 28.dp.toPx()
        val horizontal = obtainRuntimeShader("pui-progressive-x", PROGRESSIVE_SHADER).apply {
            setFloatUniform("height", size.height); setFloatUniform("radius", 22.dp.toPx()); setFloatUniform("origin", padding)
            setFloatUniform("axis", 1f, 0f); setFloatUniform("fade", 0f)
        }
        val vertical = obtainRuntimeShader("pui-progressive-y", PROGRESSIVE_SHADER).apply {
            setFloatUniform("height", size.height); setFloatUniform("radius", 22.dp.toPx()); setFloatUniform("origin", padding)
            setFloatUniform("axis", 0f, 1f); setFloatUniform("fade", 1f)
        }
        renderEffect = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(vertical,"content"), RenderEffect.createRuntimeShaderEffect(horizontal,"content"))
    }, onDrawSurface = { drawRect(Brush.verticalGradient(listOf(s.surface.copy(alpha=.52f),s.surface.copy(alpha=.14f),Color.Transparent))) }))
}
