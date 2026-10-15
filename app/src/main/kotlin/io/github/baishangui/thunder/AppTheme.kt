package io.github.baishangui.thunder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
internal fun rememberAppTheme(settings: Settings, dark: Boolean): ThemeController =
    remember(settings.colorMode, settings.accent, dark) {
        val accent = Color(settings.accent)
        val onAccent = if (accent.luminance() > .45f) Color.Black else Color.White
        ThemeController(
            colorSchemeMode = when {
                settings.colorMode != 0 && dark -> ColorSchemeMode.MonetDark
                settings.colorMode != 0 -> ColorSchemeMode.MonetLight
                dark -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.Light
            },
            lightColors = lightColorScheme(primary = accent, onPrimary = onAccent),
            darkColors = darkColorScheme(primary = accent, onPrimary = onAccent),
            keyColor = if (settings.colorMode == 2) accent else null,
        )
    }
