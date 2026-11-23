package io.github.baishangui.thunder

import android.content.SharedPreferences
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.effect.LocalCOUIMaterialEffectEnabled
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import io.github.suqi8.coui.kmp.utils.COUIOverscrollEffect
import io.github.suqi8.coui.kmp.utils.COUIOverscrollFactory

internal class AppAppearance(private val preferences: SharedPreferences) {
    var materialBlur by mutableStateOf(preferences.getBoolean("material_blur", true))
        private set
    var scrollEdgeHaptics by mutableStateOf(preferences.getBoolean("scroll_edge_haptics", true))
        private set

    fun changeMaterialBlur(enabled: Boolean) {
        materialBlur = enabled
        preferences.edit().putBoolean("material_blur", enabled).apply()
    }

    fun changeScrollEdgeHaptics(enabled: Boolean) {
        scrollEdgeHaptics = enabled
        preferences.edit().putBoolean("scroll_edge_haptics", enabled).apply()
    }
}

private data object SilentCouiOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect() = COUIOverscrollEffect(edgeHapticEnabled = false)
}

@Composable
internal fun AppTheme(settings: Settings, dark: Boolean, appearance: AppAppearance, content: @Composable () -> Unit) {
    val controller = remember(settings.colorMode, settings.accent, settings.backdropColor, dark) {
        val opaqueAccent = settings.accent or 0xff000000.toInt()
        val accent = Color(opaqueAccent)
        val onAccent = Color(AdaptiveTextColor.foreground(opaqueAccent))
        ThemeController(
            colorSchemeMode = when {
                settings.colorMode != 0 && dark -> ColorSchemeMode.MonetDark
                settings.colorMode != 0 -> ColorSchemeMode.MonetLight
                dark -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.Light
            },
            lightColors = lightColorScheme(
                primary = accent,
                onPrimary = onAccent,
                backgroundWithCard = Color(settings.backdropColor),
            ),
            darkColors = darkColorScheme(primary = accent, onPrimary = onAccent),
            keyColor = accent.takeIf { settings.colorMode == 2 },
        )
    }
    COUITheme(controller = controller) {
        CompositionLocalProvider(
            LocalCOUIMaterialEffectEnabled provides appearance.materialBlur,
            LocalOverscrollFactory provides if (appearance.scrollEdgeHaptics) COUIOverscrollFactory else SilentCouiOverscrollFactory,
            content = content,
        )
    }
}
