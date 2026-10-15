package io.github.baishangui.thunder

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import org.json.JSONObject

internal data class Area(val blur: Boolean = false, val radius: Int = 100,
                         val tint: Boolean = false, val color: Int = 0x92000000.toInt(),
                         val cornerEnabled: Boolean = false, val corner: Int = 28)
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
    val cardArea: Area = Area(color = 0x33ffffff),
    val cloudCardArea: Area = Area(color = 0x20282828, corner = 20),
    val statusIconStyle: Int = 0,
    val statusBarStackSignals: Boolean = false,
    val notificationArea: Area = Area(color = 0x33ffffff),
    val volume: VolumeSettings = VolumeSettings(),
    val hideGestureBar: Boolean = false,
    val removeGestureBarInset: Boolean = false
) {
    fun bundle() = Bundle().apply {
        volume.write(this)
        putBoolean("enabled", enabled); putInt("theme", theme)
        putInt("status_bar_hidden_icons", hiddenStatusIcons)
        putInt("status_bar_icon_style", statusIconStyle)
        putBoolean("status_bar_stack_signals", statusBarStackSignals)
        putBoolean("gesture_bar_hidden", hideGestureBar)
        putBoolean("gesture_bar_remove_inset", removeGestureBarInset)
        putInt("accent", accent); putInt("backdrop_color", backdropColor)
        putInt("glass_strength", glassStrength); putInt("refraction", refraction); putInt("roundness", roundness)
        listOf("panel" to panel, "banner" to banner, "qs_card" to cardArea, "notification_card" to notificationArea, "cloud_card" to cloudCardArea).forEach { (k, a) ->
            putBoolean(k+"_blur", a.blur); putInt(k+"_radius", a.radius)
            putBoolean(k+"_tint", a.tint); putInt(k+"_color", a.color)
            if(k!="panel") { putBoolean(k+"_corner_enabled", a.cornerEnabled); putInt(k+"_corner", a.corner) }
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
        return JSONObject().put("format", "thunder.settings").put("version", 15).put("settings", data).toString(2)
    }
    companion object {
        fun from(b: Bundle): Settings {
            fun a(k: String, defaultColor: Int) = Area(b.getBoolean(k+"_blur", false),
                b.getInt(k+"_radius", 100).coerceIn(0, 200), b.getBoolean(k+"_tint", false), b.getInt(k+"_color", defaultColor),
                b.getBoolean(k+"_corner_enabled",false),b.getInt(k+"_corner",28).coerceIn(0,64))
            // v2 had independent panels; notification values are the deterministic migration source.
            val key = if (b.containsKey("panel_radius")) "panel" else "shade"
            val config = HookConfig(b)
            val cards = config.qsCards
            val rows = config.notificationCard
            return Settings(b.getBoolean("enabled", true), b.getInt("theme", 0).coerceIn(0, 2),
                b.getInt("accent", 0xff3482ff.toInt()) or (0xff shl 24),
                b.getInt("backdrop_color", 0xfff5f5f5.toInt()) or (0xff shl 24),
                b.getInt("glass_strength", 60).coerceIn(0, 100), b.getInt("refraction", 50).coerceIn(0, 100),
                b.getInt("roundness", 28).coerceIn(16, 36), a(key, 0x92000000.toInt()), a("banner", 0x33000000),
                b.getBoolean("notification_margins", false), b.getInt("notification_margin", 30).coerceIn(0, 64),
                b.getBoolean("cloud_enabled", true),
                b.getBoolean("cloud_height_enabled", false), b.getInt("cloud_height",34).coerceIn(20,64),
                b.getBoolean("cloud_corner_enabled", false), b.getInt("cloud_corner",20).coerceIn(0,48),
                a("cloud",0x20282828).copy(cornerEnabled=false,corner=28), b.getInt("status_bar_hidden_icons",0) and StatusBarIcons.ALL,
                Area(cards.blur,cards.radius,cards.tint,cards.color,cards.cornerEnabled,cards.corner),
                if(b.containsKey("cloud_card_color")) a("cloud_card",0x20282828)
                else a("cloud",0x20282828).copy(corner=b.getInt("cloud_corner",20).coerceIn(0,48)),
                if(b.getInt("status_bar_icon_style",0)==1) 1 else 0,
                b.getBoolean("status_bar_stack_signals", false),
                Area(rows.blur,rows.radius,rows.tint,rows.color,rows.cornerEnabled,rows.corner),
                volume = VolumeSettings.from(b),
                hideGestureBar = b.getBoolean("gesture_bar_hidden", false),
                removeGestureBarInset = b.getBoolean("gesture_bar_remove_inset", false))
        }
        fun parse(text: String): Settings {
            require(text.toByteArray(Charsets.UTF_8).size <= 65536) { "配置不能超过 64 KB" }
            val root = JSONObject(text)
            val version = root.opt("version")
            require(root.opt("format") == "thunder.settings" && version in setOf(3,4,5,6,7,8,9,10,11,12,13,14,15)) { "不是受支持的 thunder 配置" }
            val data = root.getJSONObject("settings")
            // Retired transition setting in old exports: validate, then discard it.
            if (version in setOf(10,11,12,13) && data.has("status_bar_follow_app")) {
                require(data.get("status_bar_follow_app") is Boolean) { "status_bar_follow_app 必须是布尔值" }
                data.remove("status_bar_follow_app")
            }
            // v13/v14 had separate button controls. The bar is now the shared source.
            if (version in setOf(13,14)) {
                val retiredBooleans = setOf("volume_button_size", "volume_buttons_blur", "volume_buttons_tint", "volume_buttons_corner_enabled")
                val retiredRanges = mapOf("volume_button" to 36..72, "volume_buttons_radius" to 0..200, "volume_buttons_corner" to 0..64)
                (retiredBooleans + retiredRanges.keys + "volume_buttons_color").forEach { key ->
                    if (data.has(key)) {
                        val value = data.get(key)
                        require(if (key in retiredBooleans) value is Boolean else value is Int) { "$key 类型错误" }
                        retiredRanges[key]?.let { require(value is Int && value in it) { "$key 超出允许范围" } }
                        data.remove(key)
                    }
                }
            }
            val defaults = Settings().bundle().apply {
                // v7 and v12 serialize rows separately; v8–v11 seed them from the shared material.
                if(version in setOf(8,9,10,11)) {
                    keySet().filter { it.startsWith("notification_card_") }.forEach { remove(it) }
                }
                if(version in setOf(3,4,5,6,7)) {
                    remove("qs_card_blur"); remove("qs_card_radius")
                    if(version == 7) {
                        val legacy = Area(color=0x33ffffff)
                        putBoolean("notification_card_blur",legacy.blur); putInt("notification_card_radius",legacy.radius)
                        putBoolean("notification_card_tint",legacy.tint); putInt("notification_card_color",legacy.color)
                        putBoolean("notification_card_corner_enabled",legacy.cornerEnabled); putInt("notification_card_corner",legacy.corner)
                    }
                }
            }
            val added = setOf("cloud_height_enabled","cloud_height","cloud_corner_enabled","cloud_corner",
                "cloud_blur","cloud_radius","cloud_tint","cloud_color")
            val v7 = setOf("qs_card_corner_enabled","qs_card_corner","banner_corner_enabled","banner_corner") +
                listOf("notification_card","cloud_card").flatMap { k ->
                    listOf("blur","radius","tint","color","corner_enabled","corner").map { "${k}_$it" }
                }
            val expected = defaults.keySet() - (if (version !in setOf(13,14,15)) VolumeSettings.keys else emptySet()) - (if (version !in setOf(13,14,15)) setOf("gesture_bar_hidden", "gesture_bar_remove_inset") else emptySet()) - (if (version !in setOf(11,12,13,14,15)) setOf("status_bar_stack_signals") else emptySet()) - (if (version !in setOf(9,10,11,12,13,14,15)) setOf("status_bar_icon_style") else emptySet()) - (if (version in setOf(3,4,5,6)) v7 else emptySet()) - (if (version == 3) added else emptySet()) -
                (if (version == 3 || version == 4) setOf("status_bar_hidden_icons") else emptySet()) -
                (if (version in setOf(3,4,5)) setOf("qs_card_tint","qs_card_color") else emptySet())
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
                "status_bar_icon_style" to 0..1, "status_bar_hidden_icons" to 0..StatusBarIcons.ALL,
                "qs_card_radius" to 0..200,"qs_card_corner" to 0..64,"banner_corner" to 0..64,"notification_card_corner" to 0..64,
                "cloud_card_corner" to 0..64,"notification_card_radius" to 0..200,"cloud_card_radius" to 0..200)
            (ranges + VolumeSettings.ranges).forEach { (k, range) -> require(!b.containsKey(k) || b.getInt(k) in range) { "$k 超出允许范围" } }
            require((b.getInt("accent") ushr 24) == 255 && (b.getInt("backdrop_color") ushr 24) == 255) { "界面配色必须为不透明颜色" }
            return from(b)
        }
        val saver = Saver<Settings, Bundle>(save = { it.bundle() }, restore = { from(it) })
    }
}
