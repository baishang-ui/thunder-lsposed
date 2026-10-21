package io.github.baishangui.thunder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable internal fun CloudOverview(settings: Settings, change: (Settings) -> Unit) {
    StyleCard("cloud", settings, change) {
        CloudAnimationControls(settings.cloudAnimation) { change(settings.copy(cloudAnimation = it)) }
        SettingsDivider()
        SettingsSectionTitle("状态栏胶囊")
        LiquidSwitchRow("自定义高度", "", settings.cloudHeightEnabled) { change(settings.copy(cloudHeightEnabled = it)) }
        if (settings.cloudHeightEnabled) LiquidValueSlider("胶囊高度", settings.cloudHeight, 20..64, " dp") { change(settings.copy(cloudHeight = it)) }
        LiquidSwitchRow("自定义胶囊圆角", "达到高度一半时呈完整胶囊", settings.cloudCornerEnabled) { change(settings.copy(cloudCornerEnabled = it)) }
        if (settings.cloudCornerEnabled) LiquidValueSlider("胶囊圆角", settings.cloudCorner, 0..128, " dp") { change(settings.copy(cloudCorner = it)) }
        MaterialOptions(settings.cloudArea, corner = false) { change(settings.copy(cloudArea = it)) }
        SettingsDivider()
        SettingsSectionTitle("展开卡片")
        CloudCardPositionControls(settings.cloudCardPosition) { change(settings.copy(cloudCardPosition = it)) }
        CompositionLocalProvider(LocalControlScope provides "style:cloud:card") {
            MaterialOptions(settings.cloudCardArea) { change(settings.copy(cloudCardArea = it)) }
        }
        SettingsDivider()
        LiquidValueSlider("应用色强度", settings.cloudLock.maskStrength, 0..200, "%") { change(settings.copy(cloudLock = settings.cloudLock.withMaskStrength(it))) }
    }
    StyleCard("cloud_lock", settings, change) {
        MaterialOptions(settings.cloudLock.area) { change(settings.copy(cloudLock = settings.cloudLock.copy(style = 2, area = it))) }
    }
    StyleCard("cloud_notification", settings, change) {
        MaterialOptions(settings.styles.area(settings, "cloud_notification")) {
            change(settings.copy(styles = settings.styles.withArea("cloud_notification", it)))
        }
    }
}
