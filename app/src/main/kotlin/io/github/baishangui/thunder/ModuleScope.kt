package io.github.baishangui.thunder

/** API 102 reserves "system" for system_server; "android" covers other framework processes. */
internal object ModuleScope {
    const val SYSTEM_SERVER = "system"
    private val names = linkedMapOf(
        "com.android.systemui" to "系统界面",
        "com.oplus.systemui.plugins" to "系统界面插件",
        SYSTEM_SERVER to "系统框架（system / system_server）",
        "com.android.launcher" to "系统桌面（逐帧动画）"
    )

    fun required(followApp: Boolean): Map<String, String> =
        names.filterKeys { (it != SYSTEM_SERVER && it != "com.android.launcher") || followApp }

    fun describe(actual: Set<String>, followApp: Boolean, supportsSystem: Boolean): String {
        if (followApp && !supportsSystem)
            return "当前框架未声明系统进程 Hook 能力，应用过渡无法加载。请检查 LSPosed 的 API 102 系统进程支持。"
        val required = required(followApp)
        val missing = required.filterKeys { it !in actual }
        if (missing.isEmpty()) return "作用域已完整：${required.values.joinToString("、")}" +
            (if (followApp) "。首次加载或更新系统框架代码后需重启手机，并确认收到系统框架与桌面动画报告。" else "")
        return "作用域缺少：${missing.values.joinToString("、")}。请在 LSPosed 的 thunder 作用域中勾选；" +
            (if (SYSTEM_SERVER in missing) "勾选 system 后需重启手机。" else "勾选后重启系统界面。") +
            (if (followApp && "android" in actual && SYSTEM_SERVER !in actual)
                "当前已选的 android 不代表 system_server；API 102 需要独立的 system 作用域。" else "")
    }
}
