package io.github.baishangui.thunder

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import org.json.JSONObject

internal data class Area(val blur: Boolean = false, val radius: Int = 100,
                         val tint: Boolean = false, val color: Int = 0x92000000.toInt())
internal data class Settings(
    val enabled: Boolean = true, val theme: Int = 0,
    val accent: Int = 0xff3482ff.toInt(), val backdropColor: Int = 0xfff5f5f5.toInt(),
    val glassStrength: Int = 60, val refraction: Int = 50, val roundness: Int = 28,
    val panel: Area = Area(), val banner: Area = Area(color = 0x33000000),
    val margins: Boolean = false, val margin: Int = 30, val cloud: Boolean = true,
    val cloudHeightEnabled: Boolean = false, val cloudHeight: Int = 34,
    val cloudCornerEnabled: Boolean = false, val cloudCorner: Int = 20,
    val cloudArea: Area = Area(color = 0x20282828),
    val hiddenStatusIcons: Int = 0,
    val qsCardTint: Boolean = false, val qsCardColor: Int = 0x33ffffff
) {
    fun bundle() = Bundle().apply {
        putBoolean("enabled", enabled); putInt("theme", theme)
        putInt("status_bar_hidden_icons", hiddenStatusIcons)
        putBoolean("qs_card_tint", qsCardTint); putInt("qs_card_color", qsCardColor)
        putInt("accent", accent); putInt("backdrop_color", backdropColor)
        putInt("glass_strength", glassStrength); putInt("refraction", refraction); putInt("roundness", roundness)
        listOf("panel" to panel, "banner" to banner).forEach { (k, a) ->
            putBoolean(k+"_blur", a.blur); putInt(k+"_radius", a.radius)
            putBoolean(k+"_tint", a.tint); putInt(k+"_color", a.color)
        }
        putBoolean("notification_margins", margins); putInt("notification_margin", margin)
        putBoolean("cloud_enabled", cloud)
        putBoolean("cloud_height_enabled", cloudHeightEnabled); putInt("cloud_height", cloudHeight)
        putBoolean("cloud_corner_enabled", cloudCornerEnabled); putInt("cloud_corner", cloudCorner)
        putBoolean("cloud_blur", cloudArea.blur); putInt("cloud_radius", cloudArea.radius)
        putBoolean("cloud_tint", cloudArea.tint); putInt("cloud_color", cloudArea.color)
    }
    fun json(): String {
        val data = JSONObject()
        bundle().let { b -> b.keySet().sorted().forEach { k -> @Suppress("DEPRECATION") data.put(k, b.get(k)) } }
        return JSONObject().put("format", "thunder.settings").put("version", 6).put("settings", data).toString(2)
    }
    companion object {
        fun from(b: Bundle): Settings {
            fun a(k: String, defaultColor: Int) = Area(b.getBoolean(k+"_blur", false),
                b.getInt(k+"_radius", 100).coerceIn(0, 200), b.getBoolean(k+"_tint", false), b.getInt(k+"_color", defaultColor))
            // v2 had independent panels; notification values are the deterministic migration source.
            val key = if (b.containsKey("panel_radius")) "panel" else "shade"
            return Settings(b.getBoolean("enabled", true), b.getInt("theme", 0).coerceIn(0, 2),
                b.getInt("accent", 0xff3482ff.toInt()) or (0xff shl 24),
                b.getInt("backdrop_color", 0xfff5f5f5.toInt()) or (0xff shl 24),
                b.getInt("glass_strength", 60).coerceIn(0, 100), b.getInt("refraction", 50).coerceIn(0, 100),
                b.getInt("roundness", 28).coerceIn(16, 36), a(key, 0x92000000.toInt()), a("banner", 0x33000000),
                b.getBoolean("notification_margins", false), b.getInt("notification_margin", 30).coerceIn(0, 64),
                b.getBoolean("cloud_enabled", true),
                b.getBoolean("cloud_height_enabled", false), b.getInt("cloud_height",34).coerceIn(20,64),
                b.getBoolean("cloud_corner_enabled", false), b.getInt("cloud_corner",20).coerceIn(0,48),
                a("cloud",0x20282828), b.getInt("status_bar_hidden_icons",0) and StatusBarIcons.ALL,
                b.getBoolean("qs_card_tint",false), b.getInt("qs_card_color",0x33ffffff))
        }
        fun parse(text: String): Settings {
            require(text.toByteArray(Charsets.UTF_8).size <= 65536) { "配置不能超过 64 KB" }
            val root = JSONObject(text)
            val version = root.opt("version")
            require(root.opt("format") == "thunder.settings" && version in setOf(3,4,5,6)) { "不是受支持的 thunder 配置" }
            val data = root.getJSONObject("settings")
            val defaults = Settings().bundle()
            val added = setOf("cloud_height_enabled","cloud_height","cloud_corner_enabled","cloud_corner",
                "cloud_blur","cloud_radius","cloud_tint","cloud_color")
            val expected = defaults.keySet() - (if (version == 3) added else emptySet()) -
                (if (version == 3 || version == 4) setOf("status_bar_hidden_icons") else emptySet()) -
                (if (version != 6) setOf("qs_card_tint","qs_card_color") else emptySet())
            require(data.keys().asSequence().toSet() == expected) { "配置字段不完整或包含未知字段" }
            val b = Bundle()
            expected.forEach { k ->
                val value = data.get(k)
                @Suppress("DEPRECATION") when (defaults.get(k)) {
                    is Boolean -> { require(value is Boolean) { "$k 必须是布尔值" }; b.putBoolean(k, value) }
                    is Int -> { require(value is Int) { "$k 必须是整数" }; b.putInt(k, value) }
                }
            }
            val ranges = mapOf("theme" to 0..2, "glass_strength" to 0..100, "refraction" to 0..100,
                "roundness" to 16..36, "panel_radius" to 0..200, "banner_radius" to 0..200, "notification_margin" to 0..64, "cloud_height" to 20..64, "cloud_corner" to 0..48, "cloud_radius" to 0..200,
                "status_bar_hidden_icons" to 0..StatusBarIcons.ALL)
            ranges.forEach { (k, range) -> require(!b.containsKey(k) || b.getInt(k) in range) { "$k 超出允许范围" } }
            require((b.getInt("accent") ushr 24) == 255 && (b.getInt("backdrop_color") ushr 24) == 255) { "界面配色必须为不透明颜色" }
            return from(b)
        }
        val saver = Saver<Settings, Bundle>(save = { it.bundle() }, restore = { from(it) })
    }
}
