package io.github.baishangui.thunder

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit

/** Only an explicit user action restarts SystemUI; automatic edits stay on the live channel. */
internal object SystemUiSettings {
    data class RestartState(val running: Boolean = false, val message: String = "")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val mutableRestart = MutableStateFlow(RestartState())
    val restartState = mutableRestart.asStateFlow()

    // Application-owned work survives rotation/backgrounding without starting another restart.
    fun restart(context: Context, settings: Settings) {
        if (!lock.tryLock()) return
        val app = context.applicationContext
        mutableRestart.value = RestartState(true, "正在保存并同步当前配置…")
        scope.launch {
            try {
                val message = try {
                    performRestart(app, settings)
                } catch (error: Exception) {
                    "重启系统界面失败：${error.message ?: "未知错误"}。未自动重试，请查看运行报告。"
                }
                mutableRestart.value = RestartState(false, message)
            } finally {
                lock.unlock()
            }
        }
    }

    private fun progress(message: String) { mutableRestart.value = RestartState(true, message) }

    private suspend fun performRestart(context: Context, settings: Settings): String {
        val receiptStarted = SystemClock.elapsedRealtime()
        val saved = SettingsWriter.submit(context, settings, immediate = true).await()
        if (saved.settings == null) return "配置保存失败：${saved.error}，未执行重启。"
        if (!saved.synced) return "配置已保存在本地，但尚未同步，未执行重启。${RemoteSettings.status}"
        val revision = SettingsProvider.preferences(context).getLong("revision", 0)
        // A fresh Hook report proves this exact process has already registered with LSPosed.
        // In 2.2.1 (7912), a Binder death within 60 seconds of registration counts as a crash.
        progress("配置已同步，正在检查当前系统界面进程…")
        val runtime = context.createDeviceProtectedStorageContext().getSharedPreferences("runtime", 0)
        var confirmedPid = -1
        var confirmedAt = -1L
        for (attempt in 0 until 40) {
            val report = runtime.all
            val pid = report["pid"] as? Int ?: -1
            val elapsed = report["elapsed"] as? Long ?: -1
            if (pid > 1 && (report["hook_version"] as? Int ?: 0) > 0 &&
                (report["revision"] as? Long ?: -1) >= revision &&
                elapsed >= receiptStarted && elapsed <= SystemClock.elapsedRealtime()) {
                confirmedPid = pid
                confirmedAt = elapsed
                break
            }
            delay(250)
        }
        if (confirmedPid < 0) return "配置已同步，但无法确认当前系统界面的运行状态，未执行重启。首次安装请正常重启手机；已有模块请检查 LSPosed 作用域和运行报告。"
        while (true) {
            val remaining = SystemUiRestartWindow.remaining(confirmedAt, SystemClock.elapsedRealtime())
            if (remaining == 0L) break
            progress("等待系统界面稳定运行，还需 ${(remaining + 999) / 1000} 秒。可以后台继续。")
            delay(minOf(remaining, 1000))
        }
        progress("正在请求 Root，通过 Android 重新启动系统界面…")
        val started = SystemClock.elapsedRealtime()
        val script = context.assets.open("restart-systemui.sh").bufferedReader().use { it.readText() }
        val deadline = System.currentTimeMillis() / 1000 + 20
        val process = ProcessBuilder("su", "-c", "sh").redirectErrorStream(true).start()
        val output: String
        val exit: Int
        try {
            process.outputStream.bufferedWriter().use {
                it.write("THUNDER_RESTART_DEADLINE=$deadline\n")
                it.write("THUNDER_RESTART_EXPECTED_PID=$confirmedPid\n")
                it.write("THUNDER_RESTART_CONFIRMED_ELAPSED=$confirmedAt\n")
                it.write(script)
            }
            if (!process.waitFor(45, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return "重启请求超时，已停止等待且不会自动重试。请检查 Root 授权和系统界面状态。"
            }
            output = process.inputStream.bufferedReader().use { it.readText() }
            exit = process.exitValue()
        } finally {
            if (process.isAlive) process.destroyForcibly()
            process.outputStream.close()
            process.inputStream.close()
            process.errorStream.close()
        }
        if (exit != 0) return when {
            "ROOT_DENIED" in output -> "Root 授权被拒绝，未重启系统界面；实时配置同步仍可使用。"
            "EXPIRED" in output -> "Root 授权等待过久，本次重启请求已过期，请稍后重试。"
            "PROCESS_CHANGED" in output -> "等待期间系统界面进程已变化，本次请求已取消，请查看运行报告。"
            "SERVICE_UNAVAILABLE" in output -> "未找到可用的系统界面服务，未执行重启。请检查固件版本。"
            "START_FAILED" in output -> "旧系统界面已停止，但 Android 未能启动服务。请正常重启手机恢复，并查看系统日志。"
            "STOP_FAILED" in output -> "Android 未能停止系统界面，本次重启已中止，请查看系统日志。"
            "NOT_STABLE" in output -> "系统界面尚未超过稳定观察期，未执行重启。请稍后再试。"
            "UNCONFIRMED_PROCESS" in output -> "无法核对系统界面的进程身份，未执行重启。请查看运行报告和固件兼容性。"
            "NOT_RUNNING" in output -> "未找到系统界面进程，未执行重启，请检查系统界面状态。"
            "NO_NEW_PROCESS" in output -> "已结束旧系统界面，但未检测到新进程。已停止重试，请检查 LSPosed 和系统日志。"
            else -> "重启系统界面失败，请检查 Root 授权和运行报告（退出码 $exit）；不会自动重试。"
        }
        fun pids(prefix: String): Set<Int> = output.lineSequence().firstOrNull { it.startsWith(prefix) }
            ?.removePrefix(prefix)?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull()?.takeIf { pid -> pid > 1 } }?.toSet().orEmpty()
        val old = pids("THUNDER_RESTART_OLD=")
        val replacement = pids("THUNDER_RESTART_NEW=")
        if (old.isEmpty() || replacement.isEmpty() || old.intersect(replacement).isNotEmpty()) {
            return "重启命令已结束，但无法确认新系统界面进程；请刷新运行报告，不会自动重试。"
        }
        progress("已检测到新系统界面，正在确认本版 Hook 和配置…")
        repeat(60) {
            val report = runtime.all
            if (SystemUiRestartReceipt.accepts(
                    report["hook_version"] as? Int ?: 0, BuildConfig.VERSION_CODE,
                    report["revision"] as? Long ?: -1, revision,
                    report["elapsed"] as? Long ?: -1, started,
                    report["pid"] as? Int ?: -1, old, replacement)) {
                val errors = (report["error"] as? String).orEmpty()
                return if (errors.isEmpty()) "系统界面已重启，已确认本版 Hook 读取当前配置。"
                else "系统界面已重启并读取配置，部分接口异常：$errors"
            }
            delay(250)
        }
        return "已检测到系统界面新进程，但未收到本版 Hook 的配置确认。请检查 LSPosed 模块、作用域及安全模式状态；不会自动重试。"
    }
}

internal object SettingsReceipt {
    fun accepts(version: Int, expectedVersion: Int, revision: Long, expectedRevision: Long,
                elapsed: Long, started: Long): Boolean =
        version == expectedVersion && revision >= expectedRevision && elapsed >= started
}

internal object SystemUiRestartReceipt {
    fun accepts(version: Int, expectedVersion: Int, revision: Long, expectedRevision: Long,
                elapsed: Long, started: Long, pid: Int, old: Set<Int>, replacement: Set<Int>): Boolean =
        pid > 1 && pid !in old && pid in replacement &&
            SettingsReceipt.accepts(version, expectedVersion, revision, expectedRevision, elapsed, started)
}

/** Wait from a report by this process, never from the previous button press. */
internal object SystemUiRestartWindow {
    private const val STABLE_MILLIS = 65_000L
    fun remaining(confirmedAt: Long, now: Long): Long =
        (STABLE_MILLIS - (now - confirmedAt)).coerceIn(0, STABLE_MILLIS)
}
