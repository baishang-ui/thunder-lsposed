package pui.c17.shade

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
    val margins: Boolean = false, val margin: Int = 30, val cloud: Boolean = true
) {
    fun bundle() = Bundle().apply {
        putBoolean("enabled", enabled); putInt("theme", theme)
        putInt("accent", accent); putInt("backdrop_color", backdropColor)
        putInt("glass_strength", glassStrength); putInt("refraction", refraction); putInt("roundness", roundness)
        listOf("panel" to panel, "banner" to banner).forEach { (k, a) ->
            putBoolean(k+"_blur", a.blur); putInt(k+"_radius", a.radius)
            putBoolean(k+"_tint", a.tint); putInt(k+"_color", a.color)
        }
        putBoolean("notification_margins", margins); putInt("notification_margin", margin)
        putBoolean("cloud_pui", cloud)
    }
    fun json(): String {
        val data = JSONObject()
        bundle().let { b -> b.keySet().sorted().forEach { k -> @Suppress("DEPRECATION") data.put(k, b.get(k)) } }
        return JSONObject().put("format", "pui.c17.settings").put("version", 3).put("settings", data).toString(2)
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
                b.getBoolean("cloud_pui", true))
        }
        fun parse(text: String): Settings {
            require(text.toByteArray(Charsets.UTF_8).size <= 65536) { "配置不能超过 64 KB" }
            val root = JSONObject(text)
            require(root.opt("format") == "pui.c17.settings" && root.opt("version") == 3) { "不是受支持的 thunder / PUI v3 配置" }
            val data = root.getJSONObject("settings")
            val defaults = Settings().bundle()
            require(data.keys().asSequence().toSet() == defaults.keySet()) { "配置字段不完整或包含未知字段" }
            val b = Bundle()
            defaults.keySet().forEach { k ->
                val value = data.get(k)
                @Suppress("DEPRECATION") when (defaults.get(k)) {
                    is Boolean -> { require(value is Boolean) { "$k 必须是布尔值" }; b.putBoolean(k, value) }
                    is Int -> { require(value is Int) { "$k 必须是整数" }; b.putInt(k, value) }
                }
            }
            val ranges = mapOf("theme" to 0..2, "glass_strength" to 0..100, "refraction" to 0..100,
                "roundness" to 16..36, "panel_radius" to 0..200, "banner_radius" to 0..200, "notification_margin" to 0..64)
            ranges.forEach { (k, range) -> require(b.getInt(k) in range) { "$k 超出允许范围" } }
            require((b.getInt("accent") ushr 24) == 255 && (b.getInt("backdrop_color") ushr 24) == 255) { "界面配色必须为不透明颜色" }
            return from(b)
        }
        val saver = Saver<Settings, Bundle>(save = { it.bundle() }, restore = { from(it) })
    }
}
