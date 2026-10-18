package io.github.baishangui.thunder

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Composable internal fun CloudOverview(settings: Settings, change: (Settings) -> Unit, open: (SettingsPage) -> Unit) {
    CompositionLocalProvider(LocalControlScope provides "cloud-master") {
        SettingsGroup {
            LiquidSwitchRow("自定义流体云外观", "统一控制胶囊、展开卡片与背景效果", settings.cloud) {
                change(settings.copy(cloud = it))
            }
            if (!settings.cloud) UiText("已暂停外观修改。下方参数仍可编辑，开启后生效。", 12, muted = true)
        }
    }
    SettingsMenuGroup("显示形态") {
        SettingsMenuItem("状态栏胶囊", buildList {
            if (settings.cloudHeightEnabled) add("高度 ${settings.cloudHeight} dp")
            if (settings.cloudCornerEnabled) add("圆角 ${settings.cloudCorner} dp")
            if (settings.cloudArea.blur || settings.cloudArea.tint) add("自定义背景")
        }.joinToString(" · ").ifEmpty { "收起时显示 · 高度、圆角与背景" }, MiuixIcons.Regular.CloudFill) {
            open(SettingsPage.CloudCapsule)
        }
        SettingsMenuItem("展开卡片", settings.cloudCardArea.summary().let {
            if (it == "跟随系统") "展开后显示 · 圆角与背景" else it
        }, MiuixIcons.Regular.Notes) { open(SettingsPage.CloudCard) }
    }
    SettingsMenuGroup("背景效果") {
        SettingsMenuItem("锁屏背景", if (settings.cloudLock.style != 0 &&
            (settings.cloudLock.area.blur || settings.cloudLock.area.tint)) settings.cloudLock.area.summary(corner = false)
            else "仅锁屏时生效 · 模糊与混色", MiuixIcons.Regular.Theme) { open(SettingsPage.CloudLock) }
        SettingsMenuItem("应用色背景", when (val strength = settings.cloudLock.maskStrength) {
            0 -> "已隐藏应用色"
            100 -> "应用提供的背景颜色 · 系统强度"
            else -> "应用提供的背景颜色 · 强度 $strength%"
        }, MiuixIcons.Regular.Layers) { open(SettingsPage.CloudAppColor) }
    }
    UiText("通知中心和锁屏通知列表中的卡片圆角，在「控制与通知 → 通知中心通知条」中调整。", 12, muted = true)
}

@Composable internal fun CloudDetails(page: SettingsPage, settings: Settings, change: (Settings) -> Unit,
                                     open: (SettingsPage) -> Unit) {
    if (!settings.cloud) SettingsGroup {
        UiText("正在编辑已保存的外观", 15, bold = true)
        UiText("流体云外观已关闭，当前调整会保存，开启后生效。", 12, muted = true)
        LiquidAction("前往流体云总览", Modifier.fillMaxWidth()) { open(SettingsPage.Cloud) }
    }
    when (page) {
        SettingsPage.CloudCapsule -> {
            SettingsGroup {
                UiText("尺寸与形状", 15, bold = true)
                LiquidSwitchRow("自定义高度", "状态栏中收起的胶囊高度", settings.cloudHeightEnabled) {
                    change(settings.copy(cloudHeightEnabled = it))
                }
                if (settings.cloudHeightEnabled) LiquidValueSlider("胶囊高度", settings.cloudHeight, 20..64, " dp") {
                    change(settings.copy(cloudHeight = it))
                }
                SettingsDivider()
                LiquidSwitchRow("自定义圆角", "胶囊外轮廓的圆角", settings.cloudCornerEnabled) {
                    change(settings.copy(cloudCornerEnabled = it))
                }
                if (settings.cloudCornerEnabled) LiquidValueSlider("圆角半径", settings.cloudCorner, 0..48, " dp") {
                    change(settings.copy(cloudCorner = it))
                }
                if (settings.cloudHeightEnabled || settings.cloudCornerEnabled) {
                    SettingsDivider()
                    LiquidAction("恢复系统尺寸与形状", Modifier.fillMaxWidth()) {
                        change(settings.copy(cloudHeightEnabled = false, cloudCornerEnabled = false))
                    }
                }
            }
            CloudMaterialControls("胶囊背景", settings.cloudArea) { change(settings.copy(cloudArea = it)) }
        }
        SettingsPage.CloudCard -> {
            SettingsGroup {
                UiText("卡片形状", 15, bold = true)
                LiquidSwitchRow("自定义圆角", "用于从胶囊展开的独立卡片", settings.cloudCardArea.cornerEnabled) {
                    change(settings.copy(cloudCardArea = settings.cloudCardArea.copy(cornerEnabled = it)))
                }
                if (settings.cloudCardArea.cornerEnabled) LiquidValueSlider("圆角半径", settings.cloudCardArea.corner, 0..64, " dp") {
                    change(settings.copy(cloudCardArea = settings.cloudCardArea.copy(corner = it)))
                }
                UiText("通知中心与锁屏通知列表的圆角跟随通知条设置。", 12, muted = true)
            }
            CloudMaterialControls("展开卡片背景", settings.cloudCardArea) { change(settings.copy(cloudCardArea = it)) }
        }
        SettingsPage.CloudLock -> CloudLockControls(settings.cloudLock) { change(settings.copy(cloudLock = it)) }
        SettingsPage.CloudAppColor -> CloudAppColorControls(settings.cloudLock) { change(settings.copy(cloudLock = it)) }
        else -> Unit
    }
}

/** Keep the material reset independent of the separately presented shape controls. */
@Composable internal fun CloudMaterialControls(title: String, area: Area, change: (Area) -> Unit) {
    AreaControls(title, area.copy(cornerEnabled = false), corner = false, showTitle = true) {
        change(it.copy(cornerEnabled = area.cornerEnabled, corner = area.corner))
    }
}
