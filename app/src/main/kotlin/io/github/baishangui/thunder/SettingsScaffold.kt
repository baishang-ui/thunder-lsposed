package io.github.baishangui.thunder

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.blur.*
import io.github.suqi8.coui.kmp.effect.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.theme.COUITheme

private val LocalSettingsBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
internal fun settingsPanelBlur(style: (Boolean) -> MaterialBlurStyle): Modifier? {
    val backdrop = LocalSettingsBackdrop.current ?: return null
    return Modifier.couiMaterialBlur(backdrop, style(COUITheme.colorScheme.isDark))
}

@Composable
internal fun SettingsScaffold(
    title: String,
    home: Boolean,
    snackbar: SnackbarHostState,
    back: (() -> Unit)?,
    searchState: TopAppBarSearchState?,
    toolbarContent: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = COUITheme.colorScheme
    val background = colors.backgroundWithCard
    val backdrop = if (LocalCOUIMaterialEffectEnabled.current && isRuntimeShaderSupported()) {
        rememberLayerBackdrop { drawRect(background); drawContent() }
    } else null
    val barMaterial = remember(backdrop) {
        backdrop?.let { source -> MaterialBackdropProvider { spec, shape ->
            Modifier.couiViewBlur(backdrop = source, shape = shape,
                style = ViewBlurStyle(spec.blurRadiusPx, spec.layer1, spec.mode1, spec.layer2, spec.mode2))
        } }
    }
    val popupMaterial = remember(backdrop) {
        backdrop?.let { source -> MaterialBlurProvider { spec, shape ->
            Modifier.couiMaterialBlur(backdrop = source, shape = shape,
                style = MaterialBlurStyle(spec.blurRadiusPx, spec.mixColor, spec.blendColor, when (spec.blendMode) {
                    MaterialBlurBlendMode.Overlay -> MaterialBlendMode.Overlay
                    MaterialBlurBlendMode.ColorDodge -> MaterialBlendMode.ColorDodge
                    MaterialBlurBlendMode.GlowOverlay -> MaterialBlendMode.GlowOverlay
                    MaterialBlurBlendMode.GlowColorDodge -> MaterialBlendMode.GlowColorDodge
                }))
        } }
    }
    val scrollBehavior = COUIScrollBehavior()
    val barColor = if (backdrop != null) Color.Transparent else background
    val glow: (@Composable BoxScope.() -> Unit)? = backdrop?.let { source ->
        { TopAppBarGlow(source, scrollBehavior.glowScrollOffsetPx(), colors.isDark, background, enabled = true) }
    }
    val navigationIcon: @Composable () -> Unit = {
        if (back != null) IconButton(onClick = back, material = false) { Icon(COUIIcons.Back, "返回") }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        topBar = {
            CompositionLocalProvider(LocalMaterialBackdrop provides barMaterial) {
                if (!home) {
                    SmallTopAppBar(title, color = barColor, glow = glow, navigationIcon = navigationIcon, actions = actions,
                        scrollBehavior = scrollBehavior, bottomContent = toolbarContent)
                } else {
                    TopAppBar(title = title, color = barColor, glow = glow, scrollBehavior = scrollBehavior,
                        navigationIcon = navigationIcon, actions = actions, searchState = searchState, bottomContent = toolbarContent,
                        expandedHeight = 116.dp, largeTitleFontSize = 40.sp)
                }
            }
        },
        bottomBar = {
            CompositionLocalProvider(LocalMaterialBackdrop provides barMaterial) { bottomBar() }
        },
        snackbarHost = { SnackbarHost(state = snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().then(if (backdrop == null) Modifier else Modifier.layerBackdrop(backdrop))) {
            CompositionLocalProvider(LocalSettingsBackdrop provides backdrop, LocalPopupMaterialBlur provides popupMaterial) {
                content(padding)
            }
        }
    }
}
