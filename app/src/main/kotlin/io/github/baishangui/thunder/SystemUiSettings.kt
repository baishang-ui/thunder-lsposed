package io.github.baishangui.thunder

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Confirm live delivery of a newly saved revision without ending any system process. */
internal object SystemUiSettings {
    suspend fun apply(context: Context, settings: Settings, progress: (String) -> Unit): String = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val result = SettingsWriter.submit(context, settings, immediate = true).await()
        if (result.settings == null) return@withContext "配置保存失败：${result.error}"
        if (!result.synced) return@withContext "已保存在本地；${RemoteSettings.status}"
        val revision = SettingsProvider.preferences(context).getLong("revision", 0)
        withContext(Dispatchers.Main) { progress("配置已同步，正在等待系统界面读取…") }
        val runtime = context.createDeviceProtectedStorageContext().getSharedPreferences("runtime", 0)
        repeat(40) {
            if (SettingsReceipt.accepts(
                    runtime.getInt("hook_version", 0), BuildConfig.VERSION_CODE,
                    runtime.getLong("revision", -1), revision,
                    runtime.getLong("elapsed", -1), started)) {
                val errors = runtime.getString("error", "").orEmpty()
                return@withContext if (errors.isEmpty()) "已确认：系统界面已读取新配置；缓存的界面参数可能需重新展开或重启手机"
                else "系统界面已读取配置，部分接口异常：$errors"
            }
            delay(250)
        }
        "配置已同步，但未收到本版系统界面的确认。首次安装或升级请重启手机；若 LSPosed 已进入安全模式，请先恢复模块启用状态再重启手机。"
    }
}

internal object SettingsReceipt {
    fun accepts(version: Int, expectedVersion: Int, revision: Long, expectedRevision: Long,
                elapsed: Long, started: Long): Boolean =
        version == expectedVersion && revision >= expectedRevision && elapsed >= started
}
