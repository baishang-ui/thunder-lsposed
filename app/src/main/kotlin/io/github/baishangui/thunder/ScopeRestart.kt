package io.github.baishangui.thunder

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit

/** Only an explicit action may restart SystemUI; independent plugin processes are untouched. */
internal object ScopeRestart {
    data class RestartState(val running: Boolean = false, val message: String = "")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val mutableRestart = MutableStateFlow(RestartState())
    val restartState = mutableRestart.asStateFlow()

    fun restart(context: Context, settings: Settings) {
        if (!lock.tryLock()) return
        val app = context.applicationContext
        progress("正在保存当前设置…")
        scope.launch {
            try {
                val message = try { performRestart(app, settings) }
                catch (error: Exception) {
                    "系统界面重启未完成：${error.message ?: "未知错误"}。不会自动重试。"
                }
                mutableRestart.value = RestartState(false, message)
            } finally { lock.unlock() }
        }
    }

    private fun progress(message: String) { mutableRestart.value = RestartState(true, message) }

    private suspend fun performRestart(context: Context, settings: Settings): String {
        val saved = SettingsWriter.submit(context, settings, immediate = true).await()
        if (saved.settings == null) return "配置保存失败：${saved.error}，未执行重启。"
        progress("正在请求 Root 并重启系统界面…")
        val script = context.assets.open("restart-scope.sh").bufferedReader().use { it.readText() }
        val input = "THUNDER_RESTART_DEADLINE=${System.currentTimeMillis() / 1000 + 60}\n" + script
        val result = execute(input) ?: return "系统界面重启请求超时，请检查界面是否恢复。不会自动重试。"
        val (exit, output) = result
        if (exit != 0) return failure(output, exit)
        if (ScopeRestartTargetResult.parse(output) == null)
            return "命令已结束，但未能确认系统界面重启结果，请检查界面是否恢复。"
        return if (saved.synced) "系统界面已重启。"
            else "系统界面已重启；当前配置尚未同步，请检查 LSPosed 连接。"
    }

    private suspend fun execute(input: String): Pair<Int, String>? = coroutineScope {
        val process = ProcessBuilder("su", "-c", "sh").redirectErrorStream(true).start()
        // Drain concurrently so service diagnostics cannot fill the pipe and block process exit.
        val output = async(Dispatchers.IO) { process.inputStream.bufferedReader().use { runCatching { it.readText() }.getOrDefault("") } }
        try {
            process.outputStream.bufferedWriter().use { it.write(input) }
            val finished = withTimeoutOrNull(90_000) {
                while (!process.waitFor(0, TimeUnit.MILLISECONDS)) delay(100)
                true
            } ?: false
            if (!finished) {
                process.destroyForcibly()
                process.inputStream.close()
                output.cancel()
                null
            } else process.exitValue() to output.await()
        } finally {
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.outputStream.close() }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }

    private fun failure(output: String, exit: Int): String {
        val error = output.lineSequence().firstOrNull { it.startsWith("THUNDER_RESTART_ERROR=") }?.substringAfter('=')
        val reason = when (error) {
            "ROOT_DENIED" -> "Root 授权被拒绝"
            "EXPIRED" -> "Root 授权等待过久，重启请求已过期"
            "PROCESS_CHANGED" -> "系统界面进程已变化，请重新点击重启"
            "START_FAILED" -> "系统界面已停止，但服务启动失败，请正常重启手机恢复"
            "STOP_FAILED" -> "系统界面停止失败"
            "OLD_PROCESS_ALIVE" -> "系统界面旧进程仍未退出，无法确认重启"
            "UNCONFIRMED_PROCESS" -> "无法核对系统界面进程身份，未执行重启"
            "BAD_PID" -> "系统界面存在无效或多个进程，未执行重启"
            "NOT_RUNNING" -> "未找到系统界面进程，未执行重启"
            "NO_NEW_PROCESS" -> "已请求启动系统界面，但未检测到新进程"
            else -> "系统界面重启未完成（退出码 $exit），请检查 Root 授权"
        }
        return "$reason。不会自动重试。"
    }
}
