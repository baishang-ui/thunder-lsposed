package io.github.baishangui.thunder

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit

/** Only an explicit action may reload the two supported module scopes. */
internal object ScopeRestart {
    data class RestartState(val running: Boolean = false, val message: String = "")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val mutableRestart = MutableStateFlow(RestartState())
    val restartState = mutableRestart.asStateFlow()

    fun restart(context: Context, settings: Settings) {
        if (!lock.tryLock()) return
        val app = context.applicationContext
        progress("正在保存并同步当前配置…")
        scope.launch {
            try {
                val message = try { performRestart(app, settings) }
                catch (error: Exception) {
                    "作用域重启未完成：${error.message ?: "未知错误"}。请检查运行报告，不会自动重试。"
                }
                mutableRestart.value = RestartState(false, message)
            } finally { lock.unlock() }
        }
    }

    private fun progress(message: String) { mutableRestart.value = RestartState(true, message) }

    private suspend fun performRestart(context: Context, settings: Settings): String {
        val receiptStarted = SystemClock.elapsedRealtime()
        val saved = SettingsWriter.submit(context, settings, immediate = true).await()
        if (saved.settings == null) return "配置保存失败：${saved.error}，未执行重启。"
        if (!saved.synced) return "配置已保存在本地，但尚未同步，未执行重启。${RemoteSettings.status}"
        val actual = RemoteSettings.actualScope() ?: return "无法读取 LSPosed 实际作用域，未执行重启。请检查框架连接。"
        if (!ModuleScope.complete(actual)) return "${ModuleScope.describe(actual)}未执行重启。"
        val revision = SettingsProvider.preferences(context).getLong("revision", 0)
        val device = context.createDeviceProtectedStorageContext()
        val hostRuntime = device.getSharedPreferences("runtime", 0)
        val pluginRuntime = device.getSharedPreferences("runtime_cloud", 0)
        fun host() = ScopeRestartReport.from(hostRuntime.all)
        fun plugin() = ScopeRestartReport.from(pluginRuntime.all)

        progress("正在核对系统界面和系统界面插件的运行报告…")
        var confirmedHost: ScopeRestartReport? = null
        var confirmedPlugin: ScopeRestartReport? = null
        // A fresh report proves that injection (and LSPosed heartbeat registration) preceded it.
        // An absent independent report never authorizes stopping an unknown plugin process.
        for (attempt in 0 until 40) {
            val now = SystemClock.elapsedRealtime()
            confirmedHost = host().takeIf { it.fresh(revision, receiptStarted, now) }
            confirmedPlugin = plugin().takeIf { it.fresh(revision, receiptStarted, now) }
            // Even a host with plugin hooks may coexist with an independent plugin process.
            // Allow that process the full report window before selecting the restart plan.
            if (confirmedHost != null && confirmedPlugin != null) break
            delay(250)
        }
        val hostReport = confirmedHost ?: return "无法确认当前系统界面进程，未执行重启。首次安装请正常重启手机；已有模块请检查作用域和运行报告。"
        val pluginReport = confirmedPlugin
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val remaining = maxOf(ScopeRestartWindow.remaining(hostReport.elapsed, now),
                pluginReport?.let { ScopeRestartWindow.remaining(it.elapsed, now) } ?: 0)
            if (remaining == 0L) break
            progress("等待作用域进程稳定运行，还需 ${(remaining + 999) / 1000} 秒。可以后台继续。")
            delay(minOf(remaining, 1000))
        }
        // Recheck the framework scope after backgrounding / the stability wait.
        if (RemoteSettings.actualScope()?.let(ModuleScope::complete) != true)
            return "等待期间 LSPosed 连接或作用域发生变化，已取消重启。"
        progress("正在请求 Root，重新加载系统界面及插件作用域…")
        val started = SystemClock.elapsedRealtime()
        val script = context.assets.open("restart-scope.sh").bufferedReader().use { it.readText() }
        val input = """
            THUNDER_RESTART_DEADLINE=${System.currentTimeMillis() / 1000 + 20}
            THUNDER_RESTART_EXPECTED_PID=${hostReport.pid}
            THUNDER_RESTART_CONFIRMED_ELAPSED=${hostReport.elapsed}
            THUNDER_RESTART_PLUGIN_PID=${pluginReport?.pid ?: 0}
            THUNDER_RESTART_PLUGIN_ELAPSED=${pluginReport?.elapsed ?: 0}
        """.trimIndent() + "\n" + script
        val result = execute(input) ?: return "作用域重启请求超时，已停止等待。可能已执行部分步骤，请检查界面和运行报告；不会自动重试。"
        val (exit, output) = result
        val hostResult = ScopeRestartTargetResult.parse(output, "HOST")
        val pluginResult = ScopeRestartTargetResult.parse(output, "PLUGIN")
        if (exit != 0) return failure(output, exit, pluginResult != null)
        val hosted = output.lineSequence().count { it == "THUNDER_RESTART_PLUGIN_MODE=host" } == 1
        if (hostResult == null || (!hosted && pluginResult == null) || (hosted && pluginResult != null))
            return "命令已结束，但作用域进程结果不完整。请检查运行报告；不会自动重试。"

        progress("正在分别确认两个作用域的本版 Hook 与配置…")
        var hostReady = false
        var pluginReady = false
        repeat(60) {
            val now = SystemClock.elapsedRealtime()
            val newHost = host()
            val newPlugin = plugin()
            hostReady = newHost.replacement(hostResult, BuildConfig.VERSION_CODE, revision, started, now)
            pluginReady = if (hosted) hostReady && newHost.pluginLoaded
                else newPlugin.replacement(pluginResult!!, BuildConfig.VERSION_CODE, revision, started, now) && newPlugin.pluginLoaded
            if (hostReady && pluginReady) {
                val errors = listOf(newHost.error, if (hosted) "" else newPlugin.error).filter(String::isNotEmpty)
                return if (errors.isEmpty()) "作用域重启完成：系统界面、系统界面插件均已加载本版 Hook 和当前配置。"
                else "两个作用域已重新加载，部分接口异常：${errors.joinToString("；")}"
            }
            delay(250)
        }
        val pending = listOfNotNull(if (!hostReady) "系统界面" else null, if (!pluginReady) "系统界面插件" else null)
        return "重启命令已执行，但${pending.joinToString("、")}尚未确认本版 Hook 和配置。请检查 LSPosed 及运行报告；不会自动重试。"
    }

    private suspend fun execute(input: String): Pair<Int, String>? = coroutineScope {
        val process = ProcessBuilder("su", "-c", "sh").redirectErrorStream(true).start()
        // Drain concurrently: a verbose service error must not block the process before waitFor.
        val output = async(Dispatchers.IO) { process.inputStream.bufferedReader().use { runCatching { it.readText() }.getOrDefault("") } }
        try {
            process.outputStream.bufferedWriter().use { it.write(input) }
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
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

    private fun failure(output: String, exit: Int, pluginRestarted: Boolean): String {
        val target = if (output.lineSequence().any { it == "THUNDER_RESTART_TARGET=PLUGIN" }) "系统界面插件" else "系统界面"
        val error = output.lineSequence().firstOrNull { it.startsWith("THUNDER_RESTART_ERROR=") }?.substringAfter('=')
        val reason = when (error) {
            "ROOT_DENIED" -> "Root 授权被拒绝"
            "EXPIRED" -> "重启请求已过期"
            "PLUGIN_UNCONFIRMED" -> "插件独立进程没有本次配置的有效报告，已取消整个重启请求"
            "PROCESS_CHANGED" -> "$target 进程已变化，已取消后续步骤"
            "SERVICE_UNAVAILABLE" -> "$target 服务不可用，未执行停止"
            "START_FAILED" -> "$target 已停止，但服务启动失败，请正常重启手机恢复"
            "STOP_FAILED" -> "$target 停止失败，已中止后续步骤"
            "NOT_STABLE" -> "$target 尚未超过稳定观察期，已取消后续步骤"
            "UNCONFIRMED_PROCESS" -> "无法核对 $target 进程身份，已取消后续步骤"
            "BAD_PID" -> "$target 存在无效或多个进程，已取消后续步骤"
            "NOT_RUNNING" -> "未找到 $target 进程，已取消后续步骤"
            "NO_NEW_PROCESS" -> "$target 服务已请求启动，但未检测到新进程"
            else -> "作用域重启未完成（退出码 $exit）"
        }
        return (if (pluginRestarted) "系统界面插件已重启；" else "") + reason + "。请检查运行报告；不会自动重试。"
    }
}
