package io.github.baishangui.thunder

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import com.kyant.backdrop.catalog.components.LiquidButton

/** C17 four-column panel, measured in phone dp; icons come from the installed SystemUI package. */
@Composable internal fun C17ControlCenterPreview(settings: Settings) {
    val card = settings.cardArea
    MaterialSample(settings.panel, Modifier.fillMaxSize(), defaultCorner = 0, defaultColor = Color(0x30101218), defaultBlur = 44f,
        saturation = if (settings.panelSaturationEnabled) settings.panelSaturation / 100f else 1f) {
        PreviewLabel("中国联通 | 中国联通", Modifier.offset(36.dp, 18.dp), size = 12)
        NativePreviewIcon("oplus_qs_setting_icon", Modifier.offset(304.dp, 15.dp).size(20.dp), fallback = "ic_settings")
        listOf(50 to "WLAN", 126 to "蓝牙").forEachIndexed { index, (top, title) ->
            MaterialSample(card, Modifier.offset(36.dp, top.dp).size(136.dp, 60.dp), defaultCorner = 30) {
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.size(40.dp), shape = CircleShape,
                        color = if (index == 1) Color.White else Color.White.copy(alpha = .12f)) {
                        Box(contentAlignment = Alignment.Center) {
                            NativePreviewIcon(if (index == 0) "status_bar_qs_wifi_signal_4_ic" else "qs_bluetooth_icon_on", Modifier.size(26.dp),
                                tint = if (index == 1) Color(0xff006aff) else PreviewInk)
                        }
                    }
                    PreviewLabel(title, size = 15, bold = true)
                }
            }
        }
        MaterialSample(card, Modifier.offset(188.dp, 50.dp).size(136.dp), defaultCorner = 30) {
            NativePreviewIcon("ic_oplus_media_panel_cover_default", Modifier.offset(14.dp, 14.dp).size(42.dp), tint = Color.Unspecified)
            NativePreviewIcon("ic_media_speaker_device", Modifier.offset(100.dp, 15.dp).size(22.dp))
            PreviewLabel("未在播放", Modifier.offset(14.dp, 72.dp), size = 14, color = Color(0xffb5b5b9))
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                C17MediaIcon("ic_media_prev", 22, .45f)
                C17MediaIcon("ic_media_play_button", 24)
                C17MediaIcon("ic_media_next", 22, .45f)
            }
        }
        SoundModes(card, Modifier.offset(36.dp, 202.dp).size(136.dp, 60.dp))
        listOf("ic_device_display" to 36, "qs_auto_rotate_icon_on" to 112).forEach { (icon, left) ->
            MaterialSample(card, Modifier.offset(left.dp, 278.dp).size(60.dp), defaultCorner = 30) {
                NativePreviewIcon(icon, Modifier.align(Alignment.Center).size(28.dp))
            }
        }
        QsSlider(card, Modifier.offset(188.dp, 202.dp).size(60.dp, 136.dp), brightness = true)
        QsSlider(card, Modifier.offset(264.dp, 202.dp).size(60.dp, 136.dp), brightness = false)
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 17.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(Modifier.size(5.dp), shape = CircleShape, color = Color.White) {}
            Surface(Modifier.size(5.dp), shape = CircleShape, color = Color.White.copy(alpha = .35f)) {}
        }
    }
}

@Composable private fun C17MediaIcon(name: String, size: Int = 24, alpha: Float = 1f) {
    NativePreviewIcon(name, Modifier.size(size.dp), tint = PreviewInk.copy(alpha = alpha), fallback = "ic_music_note")
}

@Composable private fun SoundModes(area: Area, modifier: Modifier) {
    MaterialSample(area, modifier, defaultCorner = 30) {
        Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            listOf("systemui_icon_volume_ringer", "systemui_icon_volume_ringer_vibrate", "systemui_icon_volume_ringer_mute").forEachIndexed { index, name ->
                Surface(Modifier.weight(1f).aspectRatio(1f), shape = CircleShape,
                    color = if (index == 1) Color.White.copy(alpha = .18f) else Color.Transparent) {
                    Box(contentAlignment = Alignment.Center) {
                        NativePreviewIcon(name, Modifier.size(25.dp), tint = PreviewInk.copy(alpha = if (index == 1) 1f else .65f), fallback = "ic_volume_ringer")
                    }
                }
            }
        }
    }
}

@Composable private fun QsSlider(area: Area, modifier: Modifier, brightness: Boolean) {
    MaterialSample(area, modifier, defaultCorner = 30) {
        LinearProgressIndicator(
            modifier = Modifier.align(Alignment.Center).wrapContentSize(unbounded = true).requiredWidth(136.dp).rotate(-90f),
            progress = if (brightness) .53f else 0f, height = 60.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = Color(0xfff6f8ff), backgroundColor = Color.Transparent))
        NativePreviewIcon(if (brightness) "status_bar_qs_auto_brigtness_active" else "status_bar_qs_icon_volume_media_mute",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(28.dp),
            tint = if (brightness) Color(0xffffb000) else Color(0xffa0a2ad))
    }
}

@Composable internal fun C17NotificationPreview(settings: Settings, page: SettingsPage) {
    val banner = page == SettingsPage.Banner
    if (!banner) MaterialSample(settings.panel, Modifier.fillMaxSize(), defaultCorner = 0, defaultColor = Color(0x28101218), defaultBlur = 44f,
        saturation = if (settings.panelSaturationEnabled) settings.panelSaturation / 100f else 1f)
    if (banner) {
        PreviewLabel("9:41", Modifier.offset(24.dp, 17.dp), size = 14, bold = true)
        NativePreviewIcon("stat_signal_wifi_signal_4_os17", Modifier.offset(296.dp, 16.dp).size(20.dp))
    } else {
        PreviewLabel("10月17日周六", Modifier.offset(26.dp, 20.dp), size = 12)
        PreviewLabel("09:41", Modifier.offset(24.dp, 38.dp), size = 36, bold = true)
    }
    val margin = if (settings.margins) settings.margin else 30
    val start = if (banner) 54 else 100
    repeat(if (banner) 1 else 2) { index ->
        MaterialSample(if (banner) settings.banner else settings.notificationArea,
            Modifier.offset(margin.dp, (start + index * 70).dp).size((360 - margin * 2).dp, 60.dp)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (index == 0) Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(28.dp))
                else NativePreviewIcon("ic_music_note", Modifier.size(28.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PreviewLabel(if (index == 0) "thunder" else "音乐", Modifier.weight(1f), size = 13, bold = true)
                        PreviewLabel("现在", size = 11, color = PreviewInk.copy(alpha = .55f))
                    }
                    PreviewLabel(if (index == 0) "这是一条示例通知" else "正在播放你喜欢的音乐", size = 13)
                }
            }
        }
    }
    if (!banner) LiquidButton(onClick = {}, backdrop = LocalPreviewBackdrop.current,
        modifier = Modifier.offset(150.dp, 256.dp).size(60.dp, 48.dp), isInteractive = false,
        surfaceColor = Color.White.copy(alpha = .16f)) {
        Icon(MiuixIcons.Regular.Close, contentDescription = null, modifier = Modifier.size(18.dp), tint = PreviewInk)
    }
}

@Composable internal fun C17CloudPreview(settings: Settings, page: SettingsPage, appSample: Int) {
    val active = settings.cloud
    val lock = settings.cloudLock
    val locked = page == SettingsPage.CloudLock
    val resources = LocalNativePreviewResources.current
    val lockArea = when { !active || lock.style == 0 -> Area(); lock.style == 1 -> Area(tint = true, color = 0x3cd8d8d8); else -> lock.area }
    val capsule = if (!active) Area() else if (locked) lockArea else settings.cloudArea
    val card = if (!active) Area() else if (locked) lockArea else settings.cloudCardArea
    val height = if (active && settings.cloudHeightEnabled && !locked) settings.cloudHeight.toFloat() else resources.dimension("capsule_height", 30f, plugin = true)
    val corner = if (active && settings.cloudCornerEnabled) settings.cloudCorner.toFloat() else resources.dimension("capsule_corner_radius", 20f, plugin = true)
    val strength = when { !active -> 1f; !lock.appColor -> 0f; lock.strengthEnabled -> lock.strength / 100f; else -> 1f }
    val appColor = if (appSample == 0) Color(0xffdf9e7d) else Color(0xff559bef)
    val overlay = appColor.copy(alpha = (.4f * strength).coerceIn(0f, 1f))
    if (locked) {
        PreviewLabel("09:41", Modifier.offset(30.dp, 24.dp), size = 64)
        PreviewLabel("10月17日  星期六", Modifier.offset(35.dp, 108.dp), size = 13)
    } else {
        PreviewLabel("9:41", Modifier.offset(24.dp, 23.dp), size = 14, bold = true)
        NativePreviewIcon("stat_signal_wifi_signal_4_os17", Modifier.offset(305.dp, 22.dp).size(20.dp))
    }
    MaterialSample(capsule.copy(cornerEnabled = true, corner = corner.roundToInt()),
        Modifier.offset(104.dp, if (locked) 152.dp else 15.dp).size(152.dp, height.dp), defaultColor = Color(0xe6111114)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NativePreviewIcon(if (appSample == 0) "ic_music_note" else "qs_location_icon_on", Modifier.size(18.dp), tint = appColor)
            PreviewLabel(if (appSample == 0) "正在播放" else "8分钟后到达", Modifier.weight(1f), size = 11)
            Waveform(appColor)
        }
    }
    if (page == SettingsPage.CloudCapsule) return
    MaterialSample(card, Modifier.offset(16.dp, if (locked) 204.dp else 68.dp).size(328.dp, 174.dp),
        defaultCorner = resources.dimension("capsule_card_corner_radius", 26f, plugin = true).roundToInt(), defaultColor = Color(0x731b1b20)) {
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(overlay, overlay.copy(alpha = overlay.alpha * .1f)))))
        if (appSample == 0) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.thunder_preview_wallpaper), null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PreviewLabel("山间来信", size = 16, bold = true)
                    PreviewLabel("音乐 · 正在播放", size = 12, color = PreviewInk.copy(alpha = .62f))
                }
                Waveform(appColor)
            }
            Column(Modifier.offset(16.dp, 85.dp).width(296.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), progress = .38f, height = 2.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = PreviewInk, backgroundColor = Color.White.copy(alpha = .18f)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PreviewLabel("01:24", size = 9, color = PreviewInk.copy(alpha = .55f))
                    PreviewLabel("03:42", size = 9, color = PreviewInk.copy(alpha = .55f))
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 17.dp).width(180.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                C17MediaIcon("ic_media_prev", 28)
                C17MediaIcon("ic_media_pause_button", 32)
                C17MediaIcon("ic_media_next", 28)
            }
        } else {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NativePreviewIcon("qs_location_icon_on", Modifier.size(38.dp), tint = appColor)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PreviewLabel("距离目的地 2.4 公里", size = 16, bold = true)
                    PreviewLabel("沿当前道路直行", size = 12, color = PreviewInk.copy(alpha = .7f))
                }
            }
            PreviewLabel("预计 8 分钟后到达", Modifier.offset(22.dp, 109.dp), size = 20)
        }
    }
    if (locked) Box(Modifier.offset(124.dp, 391.dp).size(112.dp, 4.dp).background(Color.White.copy(alpha = .7f), CircleShape))
}

@Composable private fun Waveform(color: Color) {
    Icon(MiuixIcons.Regular.Music, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
}

@Composable internal fun C17VolumePreview(value: VolumeSettings, expanded: Boolean) {
    val resources = LocalNativePreviewResources.current
    val width = if (value.size) value.width.toFloat() else resources.dimension("volume_dialog_slider_width", 52f)
    val height = if (value.size) value.height.toFloat() else resources.dimension("volume_dialog_slider_height", 254f)
    if (!expanded) {
        Column(Modifier.offset((334 - width).dp, 25.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            VolumeTrack(value.bar, width, height, .62f, "systemui_icon_volume_media", filled = true)
            MaterialSample(value.bar, Modifier.size(width.dp), defaultCorner = (width / 2).roundToInt()) {
                NativePreviewIcon("ic_more_vert", Modifier.align(Alignment.Center).size(26.dp))
            }
        }
        return
    }
    val count = if (value.compact) 2 else 3
    val panelWidth = (width * count + (count - 1) * 10 + 32).coerceIn(190f, 328f)
    val trackWidth = minOf(width, (panelWidth - 32 - (count - 1) * 10) / count)
    val footer = (width * .85f).coerceIn(42f, 64f)
    MaterialSample(value.panel, Modifier.offset((344 - panelWidth).dp, 20.dp).size(panelWidth.dp, (height + footer + 44).dp)) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                repeat(count) { index -> VolumeTrack(value.bar, trackWidth, height, if (index == count - 1) .74f else 0f,
                    if (index == count - 1) "systemui_icon_volume_media" else if (index == 0) "systemui_icon_volume_ringer_mute" else "systemui_icon_volume_alarm",
                    filled = index == count - 1) }
            }
            Row(Modifier.fillMaxWidth().height(footer.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SoundModes(value.bar, Modifier.weight(3f).fillMaxHeight())
                MaterialSample(value.bar, Modifier.weight(1f).fillMaxHeight(), defaultCorner = 32) {
                    NativePreviewIcon("qs_dnd_icon_on", Modifier.align(Alignment.Center).size(25.dp))
                }
            }
        }
    }
}

@Composable private fun VolumeTrack(area: Area, width: Float, height: Float, level: Float, icon: String, filled: Boolean) {
    MaterialSample(area, Modifier.size(width.dp, height.dp), defaultCorner = (width / 2).roundToInt()) {
        LinearProgressIndicator(
            modifier = Modifier.align(Alignment.Center).wrapContentSize(unbounded = true).requiredWidth(height.dp).rotate(-90f),
            progress = if (filled) level else 0f, height = width.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = Color(0xfff6f6f8), backgroundColor = Color.Transparent))
        NativePreviewIcon(icon, Modifier.align(Alignment.BottomCenter).padding(bottom = 15.dp).size(26.dp),
            tint = if (filled) Color(0xff38383b) else Color(0xffb5b5b9), fallback = "ic_volume_media")
    }
}
