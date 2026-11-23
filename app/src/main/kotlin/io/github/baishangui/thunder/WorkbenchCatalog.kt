package io.github.baishangui.thunder

import android.content.SharedPreferences
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.suqi8.coui.kmp.nav.core.NavKey

internal enum class WorkbenchSection(val page: SettingsPage, @StringRes val label: Int) {
    Background(SettingsPage.Panel, R.string.workbench_background),
    Notification(SettingsPage.Panel, R.string.workbench_notification),
    Banner(SettingsPage.Panel, R.string.workbench_banner),
    Tiles(SettingsPage.Panel, R.string.workbench_tiles),
    VolumeSize(SettingsPage.Volume, R.string.workbench_size),
    VolumeMotion(SettingsPage.Volume, R.string.workbench_motion),
    VolumeMaterial(SettingsPage.Volume, R.string.workbench_material),
    CloudCapsule(SettingsPage.Cloud, R.string.workbench_capsule),
    CloudCard(SettingsPage.Cloud, R.string.workbench_expanded_card),
    CloudMotion(SettingsPage.Cloud, R.string.workbench_motion),
    CloudLock(SettingsPage.Cloud, R.string.workbench_lockscreen),
    CloudNotification(SettingsPage.Cloud, R.string.workbench_notification_center),
    StatusLayout(SettingsPage.StatusBar, R.string.workbench_layout),
    StatusClock(SettingsPage.StatusBar, R.string.workbench_clock),
    StatusIcons(SettingsPage.StatusBar, R.string.workbench_icons),
    GestureDisplay(SettingsPage.GestureBar, R.string.workbench_display),
    GestureSize(SettingsPage.GestureBar, R.string.workbench_size),
    GestureMaterial(SettingsPage.GestureBar, R.string.workbench_material),
}

internal data class SettingsDestination(val page: SettingsPage, val parameterId: String? = null) : NavKey

internal class WorkbenchParameter(field: Bundle, val section: WorkbenchSection) {
    val key = requireNotNull(field.getString("key"))
    val bit = field.getInt("bit")
    val id = if (bit == 0) key else "$key:$bit"
    val group = requireNotNull(field.getString("style_group"))
    val title = when (key) {
        "status_bar_clock_seconds" -> "显示秒数"
        "status_bar_icon_style" -> "图标方案"
        else -> requireNotNull(field.getString("title")).let { title ->
            when {
                key.endsWith("_radius") -> "模糊半径"
                key.endsWith("_corner") && title == "圆角" -> "圆角半径"
                key.endsWith("_color") && title == "混色" -> "混色颜色"
                else -> title
            }
        }
    }
    val description = field.getString("description").orEmpty()
    val subsection = field.getString("subsection").orEmpty()
    val unit = field.getString("unit").orEmpty().let { if (it.isEmpty() || it == "%") it else " $it" }
    val color = field.getBoolean("color")
    val range = if (field.containsKey("min")) field.getInt("min")..field.getInt("max") else null
    val choices = field.getStringArray("choices")?.toList()
    val choiceValues = field.getIntArray("choice_values")?.toList() ?: choices?.indices?.toList().orEmpty()
    private val prerequisites = field.getStringArray("visibility_keys").orEmpty()
        .zip(field.getBooleanArray("visibility_values")?.toList().orEmpty())
        .filterNot { (key, _) -> key.startsWith("style_") || key == "status_bar_clock" || key == "gesture_style" }
    val pinAvailable = choices == null && !color && !key.startsWith("style_")

    fun visible(values: Bundle) = prerequisites.all { (key, expected) -> values.getBoolean(key) == expected }
    fun checked(values: Bundle) = if (bit == 0) values.getBoolean(key) else values.getInt(key) and bit != 0
    fun number(values: Bundle) = values.getInt(key)
    fun selected(values: Bundle) = choiceValues.indexOf(number(values)).coerceAtLeast(0)
    fun edit(settings: Settings, value: Any) = SystemSettingsCatalog.patch(settings, key, value, bit)
    val destination get() = SettingsDestination(section.page, id)
}

internal object WorkbenchCatalog {
    val pages = listOf(SettingsPage.Panel, SettingsPage.Volume, SettingsPage.Cloud, SettingsPage.StatusBar, SettingsPage.GestureBar)
    val tileGroups = listOf("qs_small", "qs_wide", "qs_slider", "qs_music")

    private fun section(key: String, group: String) = when (group) {
        "panel" -> WorkbenchSection.Background
        "notification" -> WorkbenchSection.Notification
        "banner" -> WorkbenchSection.Banner
        in tileGroups -> WorkbenchSection.Tiles
        "volume" -> when {
            key.startsWith("volume_bar_") || key.startsWith("volume_panel_") -> WorkbenchSection.VolumeMaterial
            key in setOf("volume_compact", "volume_liquid_entrance") -> WorkbenchSection.VolumeMotion
            else -> WorkbenchSection.VolumeSize
        }
        "cloud" -> when {
            key.startsWith("cloud_animation_") -> WorkbenchSection.CloudMotion
            key.startsWith("cloud_card_") -> WorkbenchSection.CloudCard
            else -> WorkbenchSection.CloudCapsule
        }
        "cloud_lock" -> WorkbenchSection.CloudLock
        "cloud_notification" -> WorkbenchSection.CloudNotification
        "status_layout" -> WorkbenchSection.StatusLayout
        "status_clock" -> WorkbenchSection.StatusClock
        "status_icons" -> WorkbenchSection.StatusIcons
        "gesture" -> when {
            key.startsWith("gesture_bar_") -> WorkbenchSection.GestureDisplay
            key in setOf("gesture_length", "gesture_thickness", "gesture_bottom") -> WorkbenchSection.GestureSize
            else -> WorkbenchSection.GestureMaterial
        }
        else -> null
    }

    val parameters: List<WorkbenchParameter> by lazy {
        SystemSettingsCatalog.schema().mapNotNull { field ->
            val key = requireNotNull(field.getString("key"))
            val group = field.getString("style_group") ?: return@mapNotNull null
            if (key.startsWith("style_")) return@mapNotNull null
            section(key, group)?.let { WorkbenchParameter(field, it) }
        }
    }
    private val byId by lazy { parameters.associateBy(WorkbenchParameter::id) }
    fun parameter(id: String?) = byId[id]
    fun sections(page: SettingsPage?) = WorkbenchSection.entries.filter { it.page == page }
    fun group(section: WorkbenchSection, tile: String) = when (section) {
        WorkbenchSection.Tiles -> tile
        else -> parameters.first { it.section == section }.group
    }
}

internal class WorkbenchShortcuts(private val preferences: SharedPreferences) {
    var pins by mutableStateOf(load("pins"))
        private set
    var recent by mutableStateOf(load("recent"))
        private set

    private fun load(key: String) = preferences.getString(key, null).orEmpty().split(',').filter { id ->
        val parameter = WorkbenchCatalog.parameter(id)
        parameter != null && (key != "pins" || parameter.pinAvailable)
    }.distinct()

    fun setPinned(id: String, pinned: Boolean) {
        if (WorkbenchCatalog.parameter(id)?.pinAvailable != true) return
        keepPins(if (pinned) (pins + id).distinct() else pins - id)
    }

    fun keepPins(ids: List<String>) {
        pins = ids
        preferences.edit().putString("pins", pins.joinToString(",")).apply()
    }

    fun record(id: String) {
        recent = (listOf(id) + (recent - id)).take(6)
        preferences.edit().putString("recent", recent.joinToString(",")).apply()
    }
}
