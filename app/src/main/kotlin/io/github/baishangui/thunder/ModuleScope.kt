package io.github.baishangui.thunder

internal object ModuleScope {
    private val names = linkedMapOf(
        "com.android.systemui" to "系统界面",
        "com.oplus.systemui.plugins" to "系统界面插件",
        "com.android.settings" to "设置"
    )

    fun complete(actual: Set<String>): Boolean = names.keys.all { it in actual }

    fun describe(actual: Set<String>): String {
        val missing = names.filterKeys { it !in actual }
        if (missing.isEmpty()) return "作用域已完整：${names.values.joinToString("、")}"
        return "作用域缺少：${missing.values.joinToString("、")}。请在 LSPosed 的 thunder 作用域中勾选，勾选后正常重启手机。"
    }
}
