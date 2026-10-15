package pui.c17.shade

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Separates a successful shell request from a new hook reading the saved settings. */
internal object SystemUiRestarter {
    suspend fun restart(context: Context, progress: (String) -> Unit): String = withContext(Dispatchers.IO) {
        if (!RemoteSettings.publish(context)) return@withContext "未重启：${RemoteSettings.status}。请确认 LSPosed 已启用本模块并重新打开应用。"
        val revision = SettingsProvider.preferences(context).getLong("revision", 0)
        val started = SystemClock.elapsedRealtime()
        val script = context.assets.open("restart-systemui.sh").bufferedReader().use { it.readText() }
        withContext(Dispatchers.Main) { progress("正在请求 Root 并重启系统界面…") }
        val result = runCatching {
            val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
            try {
                if (!process.waitFor(35, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    error("Root 请求超时，未确认重启；请检查授权")
                }
                val output = process.inputStream.bufferedReader().use { it.readText() }
                if (process.exitValue() != 0) error(when {
                    "ROOT_DENIED" in output -> "Root 授权未通过，系统界面未重启"
                    "NOT_RUNNING" in output -> "没有找到 SystemUI 进程，未发送重启信号"
                    "NO_NEW_PROCESS" in output -> "已结束原进程，但未等到新的 SystemUI；请手动重启手机"
                    "KILL_FAILED" in output -> "结束 SystemUI 失败，请检查 Root 授权"
                    else -> "重启命令失败，请检查 Root 授权"
                })
                RestartReceipt.parse(output) ?: error("没有收到有效的新进程信息，未确认重启")
            } finally { process.destroy() }
        }
        val receipt = result.getOrElse { return@withContext it.message ?: "无法调用 su，请检查 Root 授权" }
        withContext(Dispatchers.Main) { progress("SystemUI 已换为进程 ${receipt.newPids.joinToString()}，正在确认新配置…") }
        val runtime = context.createDeviceProtectedStorageContext().getSharedPreferences("runtime", 0)
        var fresh = false
        repeat(40) {
            val pid = runtime.getInt("pid", -1)
            val elapsed = runtime.getLong("elapsed", -1)
            if (receipt.accepts(pid, elapsed, started)) {
                fresh = true
                if (runtime.getInt("hook_version", 0) == BuildConfig.VERSION_CODE && runtime.getLong("revision", -1) == revision) {
                    val errors = runtime.getString("error", "").orEmpty()
                    return@withContext if (errors.isEmpty()) "已确认：SystemUI 已重启，新进程已读取保存的配置"
                    else "SystemUI 已重启并读取配置，部分接口异常：$errors"
                }
            }
            delay(250)
        }
        if (fresh) "SystemUI 已重启，但没有确认本版 Hook 读到最新配置；请检查 LSPosed 启用状态与版本"
        else "SystemUI 已重启，但新进程没有回报；请检查 LSPosed 作用域是否包含系统界面"
    }
}
