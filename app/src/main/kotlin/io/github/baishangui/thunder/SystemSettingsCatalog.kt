package io.github.baishangui.thunder

import android.os.Bundle

/** Data only: Settings renders these fields with its own COUI preferences and resources. */
internal object SystemSettingsCatalog {
    data class Field(val key: String, val title: String, val page: String,
        val range: IntRange? = null, val choices: List<String>? = null,
        val color: Boolean = false, val dependency: String? = null, val bit: Int = 0,
        val choiceValues: List<Int>? = null)
    val pages = linkedMapOf("cloud" to "流体云", "center" to "通控中心", "volume" to "音量面板",
        "status" to "状态栏", "gesture" to "手势条")
    val fields = buildList {
        fun flag(key: String, title: String, page: String, dependency: String? = null) {
            add(Field(key, title, page, dependency = dependency))
        }
        fun number(key: String, title: String, page: String, range: IntRange, dependency: String? = null) {
            add(Field(key, title, page, range, dependency = dependency))
        }
        fun choice(key: String, title: String, page: String, vararg choices: String) {
            add(Field(key, title, page, 0..choices.lastIndex, choices.toList()))
        }
        fun material(key: String, title: String, page: String, corner: Boolean = true) {
            flag("${key}_blur", "$title · 自定义模糊", page)
            number("${key}_radius", "$title · 模糊半径（px）", page, 0..200, "${key}_blur")
            flag("${key}_tint", "$title · 自定义混色", page)
            add(Field("${key}_color", "$title · 混色颜色", page, color = true, dependency = "${key}_tint"))
            if (corner) {
                flag("${key}_corner_enabled", "$title · 自定义圆角", page)
                number("${key}_corner", "$title · 圆角（dp）", page, 0..64, "${key}_corner_enabled")
            }
        }
        flag("enabled", "启用 thunder", "home")
        StyleGroups.groups.forEach { flag("style_${it.key}", it.title, it.page) }
        HookConfig.TILE_KEYS.forEach { key -> material(key, StyleGroups.group(key).title, StyleGroups.group(key).page) }
        material("panel", "共用背景", "panel", false)
        flag("panel_saturation_enabled", "调整背景饱和度", "panel")
        number("panel_saturation", "背景饱和度（%）", "panel", 0..200, "panel_saturation_enabled")
        material("qs_card", "磁贴材质", "cards")
        material("notification_card", "通知卡片材质", "notifications")
        flag("notification_margins", "自定义通知左右边距", "notifications")
        number("notification_margin", "左右各留白（dp）", "notifications", 0..64, "notification_margins")
        material("banner", "横幅材质", "banner")
        flag("volume_liquid_entrance", "液态入场动画", "volume")
        flag("volume_compact", "紧凑展开", "volume")
        flag("volume_size", "自定义音量条尺寸", "volume")
        number("volume_width", "音量条宽度（dp）", "volume", 32..96, "volume_size")
        number("volume_height", "音量条长度（dp）", "volume", 80..400, "volume_size")
        material("volume_bar", "音量条与按钮", "volume")
        material("volume_panel", "展开面板", "volume")
        flag("cloud_enabled", "启用流体云调整", "cloud")
        flag("cloud_animation_enabled", "优化展开与收起", "cloud")
        number("cloud_animation_duration", "动画时长（ms）", "cloud", 120..1000, "cloud_animation_enabled")
        add(Field("cloud_animation_curve", "动画曲线", "cloud", 0..3,
            listOf("iOS 式缓出", "平滑缓动", "轻快缓出", "匀速"), dependency = "cloud_animation_enabled"))
        number("cloud_animation_bounce", "回弹大小（%）", "cloud", 0..100, "cloud_animation_enabled")
        flag("cloud_height_enabled", "自定义胶囊高度", "cloud")
        number("cloud_height", "胶囊高度（dp）", "cloud", 20..64, "cloud_height_enabled")
        flag("cloud_corner_enabled", "自定义胶囊圆角", "cloud")
        number("cloud_corner", "胶囊圆角（dp）", "cloud", 0..128, "cloud_corner_enabled")
        material("cloud", "胶囊背景", "cloud", false)
        material("cloud_card", "展开卡片", "cloud")
        flag("cloud_card_position_enabled", "自定义展开位置", "cloud")
        number("cloud_card_offset_x", "展开卡片水平偏移（dp）", "cloud", -100..100, "cloud_card_position_enabled")
        number("cloud_card_offset_y", "展开卡片垂直偏移（dp）", "cloud", -100..300, "cloud_card_position_enabled")
        add(Field("cloud_lock_style", "锁屏背景", "cloud", 0..2, listOf("跟随系统", "自定义材质"), choiceValues = listOf(0, 2)))
        material("cloud_lock", "锁屏材质", "cloud")
        flag("cloud_app_color", "应用提供的背景颜色", "cloud")
        flag("cloud_app_color_strength_enabled", "自定义应用色强度", "cloud")
        number("cloud_app_color_strength", "应用色强度（%）", "cloud", 0..200, "cloud_app_color_strength_enabled")
        flag("status_bar_margins", "自定义状态栏左右边距", "status")
        number("status_bar_left", "左边距（dp）", "status", 0..64, "status_bar_margins")
        number("status_bar_right", "右边距（dp）", "status", 0..64, "status_bar_margins")
        flag("status_bar_center_enabled", "自定义中间间距", "status")
        number("status_bar_center", "中间额外留白（dp）", "status", 0..80, "status_bar_center_enabled")
        flag("status_bar_compress_clock", "长时钟自动压缩", "status")
        flag("status_bar_clock", "自定义时钟", "status")
        flag("status_bar_clock_seconds", "显示秒", "status", "status_bar_clock")
        choice("status_bar_clock_hour_format", "时间制式", "status", "跟随系统", "24 小时", "12 小时")
        choice("status_bar_clock_date", "日期格式", "status", "不显示", "月/日", "年/月/日")
        flag("status_bar_clock_weekday", "显示星期", "status", "status_bar_clock")
        number("status_bar_clock_size", "时钟字号（sp）", "status", 8..24, "status_bar_clock")
        flag("status_bar_clock_bold", "时钟加粗", "status", "status_bar_clock")
        choice("status_bar_icon_style", "图标样式（需重启系统界面）", "status", "系统默认", "iOS 风格")
        flag("status_bar_stack_signals", "叠放双卡信号", "status")
        listOf("蓝牙", "闹钟", "静音 / 振动", "勿扰模式", "VPN", "个人热点", "旋转锁定", "耳机", "NFC", "高性能模式").forEachIndexed { i, title ->
            add(Field("status_bar_hidden_icons", "隐藏$title", "status", bit = 1 shl i))
        }
        flag("gesture_bar_hidden", "隐藏手势条", "gesture")
        flag("gesture_bar_remove_inset", "取消底部抬高", "gesture")
        flag("gesture_style", "修改手势条外观", "gesture")
        number("gesture_length", "长度（dp）", "gesture", 40..320, "gesture_style")
        number("gesture_thickness", "粗细（dp）", "gesture", 1..16, "gesture_style")
        number("gesture_bottom", "距底部高度（dp）", "gesture", 0..32, "gesture_style")
        material("gesture", "手势条材质", "gesture")
        flag("gesture_colors", "自定义亮暗颜色", "gesture")
        flag("gesture_auto_color", "根据背景明暗切换颜色", "gesture", "gesture_colors")
        add(Field("gesture_light_color", "亮色手势条", "gesture", color = true, dependency = "gesture_colors"))
        add(Field("gesture_dark_color", "暗色手势条", "gesture", color = true, dependency = "gesture_colors"))
    }
    .map { if (it.page in setOf("panel", "cards", "notifications", "banner")) it.copy(page = "center") else it }
    // Legacy storage flags remain accepted for compatibility; their effective value is
    // presented as one control, just as in the standalone app.
    private val internalControls = setOf("cloud_lock_style", "cloud_app_color", "cloud_app_color_strength_enabled", "status_bar_clock", "gesture_style")
    private fun section(f: Field): String = if (f.page == "home") "" else StyleGroups.group(StyleGroups.forField(f.key)).title
    /** Conditions match HookConfig semantics, including parents of dependent options. */
    private fun conditions(f: Field): Map<String, Boolean> = linkedMapOf<String, Boolean>().apply {
        fun parent(key: String) {
            put(key, true)
            fields.firstOrNull { it.key == key }?.dependency?.let { parent(it) }
        }
        if (f.key != "cloud_app_color_strength") f.dependency?.let { parent(it) }
        if (f.page != "home" && !f.key.startsWith("style_")) put("style_" + StyleGroups.forField(f.key), true)
        if (f.key.startsWith("status_bar_clock_")) put("status_bar_clock", true)
        if (f.page == "gesture" && f.key !in listOf("gesture_bar_hidden", "gesture_bar_remove_inset")) {
            put("gesture_bar_hidden", false)
            if (f.key != "gesture_style") put("gesture_style", true)
        }
    }
    fun presentationValues(stored: Bundle): Bundle = Bundle(stored).apply {
        HookConfig.STYLE_KEYS.forEach { putBoolean("style_$it", HookConfig.styleEnabled(stored, it)) }
        if (getBoolean("style_status_clock")) putBoolean("status_bar_clock", true)
        if (getBoolean("style_gesture")) putBoolean("gesture_style", true)
        if (stored.getInt("cloud_lock_style") == 0 && !HookConfig.styleEnabled(stored, "cloud_lock")) {
            putBoolean("cloud_lock_blur", false); putBoolean("cloud_lock_tint", false)
        }
        putInt("cloud_app_color_strength", when {
            !stored.getBoolean("cloud_app_color", true) -> 0
            !stored.getBoolean("cloud_app_color_strength_enabled") -> 100
            else -> stored.getInt("cloud_app_color_strength", 100)
        })
    }
    fun visible(field: Bundle, values: Bundle): Boolean {
        val keys = field.getStringArray("visibility_keys") ?: return true
        val expected = field.getBooleanArray("visibility_values") ?: return true
        return keys.indices.all { values.getBoolean(keys[it]) == expected[it] }
    }
    fun schema() = ArrayList(fields.filterNot { it.key in internalControls || it.key == "cloud_enabled" || it.key.startsWith("qs_card_") }
        .sortedWith(compareBy<Field> { if (it.page == "home") -1 else StyleGroups.groups.indexOf(StyleGroups.group(StyleGroups.forField(it.key))) }
            .thenBy { if (it.key.startsWith("style_")) 0 else 1 }).map { f -> Bundle().apply {
        putString("key", f.key)
        putString("title", when (f.key) {
            "cloud_height_enabled" -> "自定义高度"
            "cloud_height" -> "高度"
            "cloud_corner_enabled" -> "自定义圆角"
            "cloud_corner" -> "圆角"
            "cloud_app_color_strength" -> "应用色强度"
            else -> f.title.substringAfter(" · ").substringBefore("（")
        })
        putString("page", f.page); putString("section", section(f))
        if (f.page != "home") putString("style_group", StyleGroups.forField(f.key))
        putString("unit", when {
            "（ms）" in f.title -> "ms"
            "（dp）" in f.title -> "dp"
            "（px）" in f.title -> "px"
            "（sp）" in f.title -> "sp"
            "（%）" in f.title -> "%"
            else -> ""
        })
        if (f.key == "status_bar_icon_style") putString("description", "更改后需重启系统界面")
        putBoolean("color", f.color); putInt("bit", f.bit)
        f.range?.let { putInt("min", it.first); putInt("max", it.last) }
        f.choices?.let { putStringArray("choices", it.toTypedArray()) }
        f.choiceValues?.let { putIntArray("choice_values", it.toIntArray()) }
        val conditions = conditions(f)
        putStringArray("visibility_keys", conditions.keys.toTypedArray())
        putBooleanArray("visibility_values", conditions.values.toBooleanArray())
        if (f.key == "cloud_app_color_strength") putString("description", "0% 关闭应用色，100% 系统强度，200% 两倍强度")
        if (f.key == "gesture_color") putString("description", "启用亮暗颜色时，仅使用此颜色的不透明度")
        if (f.key == "gesture_auto_color") putString("description", "开启时根据背景明暗切换；关闭时跟随系统深浅模式")
        if (f.key in listOf("cloud_lock_blur", "cloud_lock_tint")) putString("description", "关闭即保留系统对应效果")
    } })
    fun patch(current: Settings, key: String, value: Any?, bit: Int): Settings {
        validate(key, value, bit)
        val b = current.bundle()
        if (key.startsWith("cloud_lock_") && key != "cloud_lock_style") {
            b.putInt("cloud_lock_style", 2)
        }
        if (key == "cloud_app_color_strength") {
            b.putBoolean("cloud_app_color", true)
            b.putBoolean("cloud_app_color_strength_enabled", true)
        }
        when {
            bit != 0 -> b.putInt(key, if (value == true) b.getInt(key) or bit else b.getInt(key) and bit.inv())
            value is Boolean -> b.putBoolean(key, value)
            value is Int -> b.putInt(key, value)
        }
        return Settings.from(b)
    }
    fun validate(key: String, value: Any?, bit: Int) {
        val field = fields.firstOrNull { it.key == key && it.bit == bit }
            ?: throw IllegalArgumentException("未知设置项")
        @Suppress("DEPRECATION") val default = Settings().bundle().get(key)
        require(if (bit != 0 || default is Boolean) value is Boolean else value is Int) { "设置类型错误" }
        field.choiceValues?.let { require(value in it) { "未知选项" } }
        field.range?.let { require(value is Int && value in it) { "数值超出范围" } }
    }
}
