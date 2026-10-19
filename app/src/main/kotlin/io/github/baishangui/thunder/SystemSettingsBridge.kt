package io.github.baishangui.thunder

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.runBlocking

/** All storage and framework IPC stay under the module UID, never Settings' UID. */
internal object SystemSettingsBridge {
    private fun load(context: Context): Settings {
        val b = Bundle()
        SettingsProvider.preferences(context).all.forEach { (key, value) -> when (value) {
            is Boolean -> b.putBoolean(key, value)
            is Int -> b.putInt(key, value)
        } }
        return Settings.from(b)
    }
    @JvmStatic @Synchronized fun call(context: Context, method: String, extras: Bundle?): Bundle {
        val current = load(context)
        var changed: Settings? = null
        when (method) {
            "ui_snapshot" -> Unit
            "ui_update" -> {
                val data = requireNotNull(extras)
                val key = requireNotNull(data.getString("key"))
                val bit = data.getInt("bit")
                @Suppress("DEPRECATION") val value = data.get("value")
                changed = SystemSettingsCatalog.patch(current, key, value, bit)
            }
            "ui_import" -> changed = Settings.parse(requireNotNull(extras?.getString("text")))
            "ui_reset" -> changed = Settings()
            "ui_restart" -> ScopeRestart.restart(context, current)
            else -> throw IllegalArgumentException("未知系统设置操作")
        }
        changed?.let {
            val result = runBlocking { SettingsWriter.submit(context, it, immediate = true).await() }
            check(result.settings != null) { result.error ?: "保存失败" }
        }
        return Bundle().apply {
            putBundle("values", load(context).bundle())
            putParcelableArrayList("fields", SystemSettingsCatalog.schema())
            putBundle("pages", Bundle().apply { SystemSettingsCatalog.pages.forEach { (key, title) -> putString(key, title) } })
            putString("status", "${RemoteSettings.activation.label}\n${RemoteSettings.status}\n${RemoteSettings.scopeStatus}")
            putString("restart", ScopeRestart.restartState.value.message)
            putString("export", load(context).json())
            putString("version", BuildConfig.VERSION_NAME)
        }
    }
}
