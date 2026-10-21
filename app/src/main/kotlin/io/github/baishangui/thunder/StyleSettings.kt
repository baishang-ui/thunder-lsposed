package io.github.baishangui.thunder

import android.os.Bundle

/** Stored overrides remain intact when a style is disabled. */
internal data class StyleSettings(
    val switches: Map<String, Boolean> = emptyMap(),
    val materials: Map<String, Area> = emptyMap()
) {
    fun write(b: Bundle) {
        HookConfig.TILE_KEYS.forEach { key ->
            val a = materials[key] ?: fallback(b, key)
            b.putBoolean("${key}_blur", a.blur); b.putInt("${key}_radius", a.radius)
            b.putBoolean("${key}_tint", a.tint); b.putInt("${key}_color", a.color)
            b.putBoolean("${key}_corner_enabled", a.cornerEnabled); b.putInt("${key}_corner", a.corner)
        }
        val original = Bundle(b)
        HookConfig.STYLE_KEYS.forEach { group -> b.putBoolean("style_$group", switches[group] ?: defaultEnabled(original, group)) }
    }
    fun enabled(settings: Settings, key: String) = settings.bundle().getBoolean("style_$key")
    fun toggle(key: String, enabled: Boolean) = copy(switches = switches + (key to enabled))
    fun area(settings: Settings, key: String): Area = materials[key] ?: fallback(settings.bundle(), key)
    fun withArea(key: String, area: Area) = copy(materials = materials + (key to area))
    companion object {
        private fun fallback(b: Bundle, key: String): Area {
            if (key == "cloud_notification") return Area(color = 0x33ffffff, corner = 20)
            val a = HookConfig.Area(b, "qs_card")
            return Area(a.blur, a.radius, a.tint, a.color, a.cornerEnabled, a.corner)
        }
        private fun defaultEnabled(b: Bundle, group: String): Boolean {
            if (group in HookConfig.TILE_KEYS) return b.getBoolean("${group}_blur") || b.getBoolean("${group}_tint") || b.getBoolean("${group}_corner_enabled")
            return HookConfig.styleEnabled(b, group)
        }
        val keys: Set<String> = setOf("cloud_lock_corner_enabled", "cloud_lock_corner") + HookConfig.STYLE_KEYS.map { "style_$it" }.toSet() + HookConfig.TILE_KEYS.flatMap { k ->
            listOf("blur", "radius", "tint", "color", "corner_enabled", "corner").map { "${k}_$it" }
        }
        val ranges = HookConfig.TILE_KEYS.flatMap { listOf("${it}_radius" to 0..200, "${it}_corner" to 0..64) }.toMap()
        fun from(b: Bundle): StyleSettings {
            val legacy = Bundle(b).apply { keys.filterNot { it.startsWith("cloud_lock_") }.forEach { remove(it) } }
            val areas = HookConfig.TILE_KEYS.associateWith { key ->
                if (b.containsKey("${key}_radius")) {
                    val a = HookConfig.Area(b, key)
                    Area(a.blur, a.radius, a.tint, a.color, a.cornerEnabled, a.corner)
                } else if (key == "cloud_notification") {
                    val c = HookConfig(b, false)
                    val a = c.notificationCard
                    Area(a.blur, a.radius, a.tint, a.color, c.cloudCardArea.cornerEnabled, c.cloudCardArea.corner)
                } else fallback(legacy, key)
            }.filter { (key, area) -> area != fallback(legacy, key) }
            val defaults = Bundle(legacy).also { StyleSettings(materials = areas).write(it) }
            val flags = HookConfig.STYLE_KEYS.associateWith { group -> HookConfig.styleEnabled(b, group) }
                .filter { (group, value) -> value != defaults.getBoolean("style_$group") }
            return StyleSettings(flags, areas)
        }
    }
}

/** Preserve explicit card enablement while changing its last enabled adjustment. */
internal fun Settings.applyStyleEdit(next: Settings): Settings {
    val current = bundle()
    val flags = HookConfig.STYLE_KEYS.associateWith { current.getBoolean("style_$it") } + next.styles.switches
    return Settings.from(next.copy(styles = next.styles.copy(switches = flags)).bundle())
}
