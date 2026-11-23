package io.github.baishangui.thunder

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.*
import io.github.suqi8.coui.kmp.theme.COUITheme

internal fun styleIcon(key: String): ImageVector = when (key) {
    "cloud" -> COUIIcons.CloudFill
    "cloud_lock" -> COUIIcons.Lock
    "cloud_notification", "notification", "banner" -> COUIIcons.Messages
    "panel" -> COUIIcons.Layers
    "qs_small", "status_icons" -> COUIIcons.GridView
    "qs_wide", "status_layout" -> COUIIcons.HorizontalSplit
    "qs_slider" -> COUIIcons.Tune
    "qs_music" -> COUIIcons.Music
    "volume" -> COUIIcons.VolumeUp
    "status_clock" -> COUIIcons.Alarm
    else -> COUIIcons.Hide
}

@Composable
internal fun SettingsIcon(icon: ImageVector, active: Boolean = true) {
    val colors = COUITheme.colorScheme
    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp),
        tint = if (active) colors.primary else colors.labelTertiary)
}
