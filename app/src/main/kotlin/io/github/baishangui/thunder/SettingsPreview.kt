package io.github.baishangui.thunder

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorFilter
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.miuixShape

internal val PreviewInk = Color(0xfffafafa)
private val PreviewSurface = Color(0x58262628)
internal val LocalPreviewBackdrop = staticCompositionLocalOf<Backdrop> { error("Missing preview backdrop") }

/** The preview consumes the same draft as the controls; it never writes settings or calls SystemUI. */
@Composable internal fun SettingsPreview(page: SettingsPage, settings: Settings) {
    if (page in setOf(SettingsPage.Configuration, SettingsPage.Runtime, SettingsPage.About,
            SettingsPage.Cloud, SettingsPage.StatusBar, SettingsPage.NotificationCenter)) return
    var comparison by rememberSaveable(page) { mutableIntStateOf(0) }
    var appSample by rememberSaveable(page) { mutableIntStateOf(0) }
    var volumeExpanded by rememberSaveable(page) { mutableIntStateOf(1) }
    var enlarged by rememberSaveable(page) { mutableStateOf(false) }
    val cloud = page in setOf(SettingsPage.CloudCapsule, SettingsPage.CloudCard, SettingsPage.CloudLock, SettingsPage.CloudAppColor)
    val appearance = page == SettingsPage.Appearance
    val effective = when {
        comparison == 1 || (!appearance && !settings.enabled) -> Settings()
        cloud -> settings.copy(cloud = true)
        else -> settings
    }
    val resources = rememberNativePreviewResources()
    CompositionLocalProvider(LocalNativePreviewResources provides resources) {
        SettingsGroup {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UiText("效果预览", 15, bold = true)
                if (!appearance) PreviewOptions(listOf(if (cloud && !settings.cloud) "已保存" else "当前", "系统"), comparison) { comparison = it }
                else UiText("实时", 12, muted = true)
            }
            if (page == SettingsPage.CloudAppColor) PreviewOptions(listOf("音乐", "出行"), appSample) { appSample = it }
            if (page == SettingsPage.Volume) PreviewOptions(listOf("收起", "展开"), volumeExpanded) { volumeExpanded = it }
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp,
                insideMargin = PaddingValues(0.dp), onClick = { enlarged = true }) {
                PreviewContents(page, effective, appSample, volumeExpanded == 1)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                UiText(when {
                    !appearance && !settings.enabled && comparison == 0 -> "模块已关闭 · 系统效果"
                    cloud && !settings.cloud && comparison == 0 -> "外观已关闭 · 预览已保存样式"
                    page == SettingsPage.CloudAppColor -> "示例应用色 · 随强度变化"
                    page == SettingsPage.StatusStyle -> "样式需重启系统界面应用"
                    else -> "系统资源 · 示例内容"
                }, 11, muted = true, modifier = Modifier.weight(1f))
                UiText("点击放大", 11, muted = true)
            }
        }
        if (enlarged) SettingsDialog(page.title + " · 效果预览", { enlarged = false }, footer = {
            LiquidAction("完成", Modifier.fillMaxWidth(), selected = true) { enlarged = false }
        }) {
            if (!appearance) PreviewOptions(listOf("当前设置", "系统效果"), comparison) { comparison = it }
            if (page == SettingsPage.CloudAppColor) PreviewOptions(listOf("音乐", "出行"), appSample) { appSample = it }
            if (page == SettingsPage.Volume) PreviewOptions(listOf("收起", "展开"), volumeExpanded) { volumeExpanded = it }
            PreviewContents(page, effective, appSample, volumeExpanded == 1)
            UiText("图标与尺寸读取当前系统，壁纸及内容使用示例。应用内容、壁纸和系统动态材质会影响实际显示。", 12, muted = true)
        }
    }
}

@Composable private fun PreviewOptions(labels: List<String>, selected: Int, change: (Int) -> Unit) {
    ChoiceTabs(labels, selected, undoable = false, select = change)
}

@Composable private fun PreviewContents(page: SettingsPage, effective: Settings, appSample: Int, volumeExpanded: Boolean) {
    when (page) {
        SettingsPage.StatusBar, SettingsPage.StatusLayout, SettingsPage.StatusClock,
        SettingsPage.StatusIcons, SettingsPage.StatusStyle -> C17StatusPreview(effective, page)
        SettingsPage.GestureBar -> GesturePreview(effective)
        SettingsPage.Appearance -> AppearancePreview(effective)
        else -> {
            val height = when (page) {
                SettingsPage.Panel, SettingsPage.Cards -> 382
                SettingsPage.CloudCapsule -> 126
                SettingsPage.CloudLock -> 408
                SettingsPage.CloudCard, SettingsPage.CloudAppColor -> 260
                SettingsPage.Banner -> 210
                SettingsPage.Volume -> (if (effective.volume.size) effective.volume.height else 254) + 134
                else -> 320
            }
            PreviewScene(height) {
                when (page) {
                    SettingsPage.Panel, SettingsPage.Cards -> C17ControlCenterPreview(effective)
                    SettingsPage.NotificationCards, SettingsPage.Layout, SettingsPage.Banner -> C17NotificationPreview(effective, page)
                    SettingsPage.CloudCapsule, SettingsPage.CloudCard, SettingsPage.CloudLock,
                    SettingsPage.CloudAppColor -> C17CloudPreview(effective, page, appSample)
                    SettingsPage.Volume -> C17VolumePreview(effective.volume, volumeExpanded)
                    else -> Unit
                }
            }
        }
    }
}

/** Scale a 360dp illustration as a whole, keeping dp proportions on narrow and wide screens. */
@Composable internal fun PreviewScene(height: Int, wallpaper: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
        val backdrop = rememberLayerBackdrop()
        CompositionLocalProvider(LocalPreviewBackdrop provides backdrop) {
            Layout(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)), content = {
                Box(Modifier.size(360.dp, height.dp).clipToBounds()) {
                    if (wallpaper) PreviewWallpaper(Modifier.matchParentSize().layerBackdrop(backdrop))
                    content()
                }
            }) { measurables, constraints ->
                val width = 360.dp.roundToPx()
                val h = height.dp.roundToPx()
                val scale = constraints.maxWidth.toFloat() / width
                val child = measurables.single().measure(Constraints.fixed(width, h))
                layout(constraints.maxWidth, (h * scale).roundToInt()) {
                    child.placeWithLayer(0, 0) {
                        scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            }
        }
    }
}

@Composable private fun PreviewWallpaper(modifier: Modifier) {
    Image(painterResource(R.drawable.thunder_preview_wallpaper), null, modifier, contentScale = ContentScale.Crop)
}

/** Blur only the wallpaper behind a surface, leaving labels sharp like the native material. */
@Composable internal fun MaterialSample(area: Area, modifier: Modifier = Modifier, defaultCorner: Int = 28,
    defaultColor: Color = PreviewSurface, defaultBlur: Float = 80f, saturation: Float = 1f,
    content: @Composable BoxScope.() -> Unit = {}) {
    val shape = miuixShape((if (area.cornerEnabled) area.corner else defaultCorner).dp)
    val source = LocalPreviewBackdrop.current
    val radius = if (area.blur) area.radius.toFloat() else defaultBlur
    val saturationFilter = remember(saturation) { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }) }
    Surface(modifier.drawPlainBackdrop(source, shape = { shape }, effects = {
        if (radius > 0) blur(radius)
        if (saturation != 1f) colorFilter(saturationFilter)
    }),
        shape = shape, color = if (area.tint) Color(area.color) else defaultColor,
        contentColor = PreviewInk,
        border = androidx.compose.foundation.BorderStroke(.5.dp, Color.White.copy(alpha = .12f))) {
        Box(content = content)
    }
}

@Composable internal fun PreviewLabel(text: String, modifier: Modifier = Modifier, size: Int = 14, color: Color = PreviewInk,
    bold: Boolean = false) {
    Text(text, modifier, color = color, fontSize = size.sp, lineHeight = (size * 1.3f).sp,
        fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable private fun GesturePreview(settings: Settings) {
    val systemDark = isSystemInDarkTheme()
    PreviewScene(240, wallpaper = false) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(false, true).forEach { light ->
                val surface = if (light) Color(0xfff6f6f8) else Color(0xff111114)
                val ink = if (light) Color(0xff17171b) else PreviewInk
                val source = rememberLayerBackdrop()
                Box(Modifier.fillMaxWidth().height(114.dp).clip(RoundedCornerShape(16.dp))) {
                    val value = settings.gestureBar
                    val custom = value.enabled
                    val thickness = if (custom) value.thickness else 4
                    val bottom = (if (custom) value.bottom else 6).coerceAtMost(28 - thickness)
                    val inset = if (settings.removeGestureBarInset) 0 else 28
                    Surface(Modifier.matchParentSize().layerBackdrop(source), color = surface) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 14.dp, bottom = inset.dp)) {
                            Text("示例应用", color = ink, fontSize = 12.sp)
                            Card(Modifier.fillMaxWidth().padding(top = 8.dp), cornerRadius = 12.dp,
                                insideMargin = PaddingValues(12.dp),
                                colors = CardDefaults.defaultColors(color = ink.copy(alpha = .045f))) {
                                Text("页面内容", color = ink.copy(alpha = .6f), fontSize = 11.sp)
                            }
                        }
                    }
                    if (!settings.hideGestureBar) {
                        val area = if (custom) value.area else Area()
                        val modeLight = if (value.autoColor) light else !systemDark
                        val base = when {
                            custom && value.colors -> Color(if (modeLight) value.darkColor else value.lightColor)
                            area.tint -> Color(area.color)
                            else -> ink
                        }
                        val alpha = if (custom && value.colors && area.tint) base.alpha * Color(area.color).alpha
                            else if (area.blur && !area.tint) base.alpha / 3f else base.alpha
                        val tint = base.copy(alpha = alpha)
                        CompositionLocalProvider(LocalPreviewBackdrop provides source) {
                            MaterialSample(area.copy(tint = true, color = tint.toArgb()), Modifier.align(Alignment.BottomCenter).padding(bottom = bottom.dp)
                                .size((if (custom) value.length else 120).dp, thickness.dp), defaultCorner = thickness / 2, defaultBlur = 0f)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun AppearancePreview(settings: Settings) {
    val style = LocalGlass.current
    Card(Modifier.fillMaxWidth(), cornerRadius = settings.roundness.dp,
        insideMargin = PaddingValues(18.dp), colors = CardDefaults.defaultColors(color = style.background)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UiText("thunder", 20, bold = true)
            UiText(if (style.dark) "深色界面" else "浅色界面", 12, muted = true)
            // Real glass over the reference wallpaper makes fog and refraction observable.
            val source = rememberLayerBackdrop()
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(settings.roundness.dp)), contentAlignment = Alignment.Center) {
                PreviewWallpaper(Modifier.matchParentSize().layerBackdrop(source))
                CompositionLocalProvider(LocalGlass provides style.copy(source = source)) {
                    LiquidAction("玻璃效果", Modifier.width(160.dp)) {}
                }
            }
        }
    }
}
