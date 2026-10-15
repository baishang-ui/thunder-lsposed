package io.github.baishangui.thunder

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** One writer survives activity recreation; stale requests can never overwrite newer changes. */
internal object SettingsWriter {
    data class Result(val settings: Settings? = null, val synced: Boolean = false, val error: String? = null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serial = AtomicLong()
    private val lock = Mutex()
    private val mutableResult = MutableStateFlow(Result())
    val result = mutableResult.asStateFlow()
    @Volatile var latest: Settings? = null
        private set
    fun submit(context: Context, settings: Settings, immediate: Boolean = false): Deferred<Result> {
        val app = context.applicationContext
        latest = settings
        val id = serial.incrementAndGet()
        return scope.async {
            if (!immediate) delay(200)
            lock.withLock {
                if (id != serial.get()) return@withLock Result(error = "已有更新的配置等待保存")
                val result = runCatching {
                    val prefs = SettingsProvider.preferences(app)
                    val edit = prefs.edit()
                    settings.bundle().let { b -> b.keySet().forEach { k ->
                        @Suppress("DEPRECATION") when (val value = b.get(k)) {
                            is Boolean -> edit.putBoolean(k, value)
                            is Int -> edit.putInt(k, value)
                        }
                    } }
                    check(edit.putLong("revision", maxOf(System.currentTimeMillis(), prefs.getLong("revision", 0) + 1)).commit()) { "本地保存失败" }
                    Result(settings, RemoteSettings.publish(app))
                }.getOrElse { Result(error = it.message ?: "保存失败") }
                if (id == serial.get()) mutableResult.value = result
                result
            }
        }
    }
}
