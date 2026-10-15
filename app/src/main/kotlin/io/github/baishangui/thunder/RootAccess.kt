package io.github.baishangui.thunder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import java.util.concurrent.TimeUnit

internal enum class RootAccessState(val label: String, val hint: String) {
    Unchecked("未验证", "点击下方按钮验证 Root 权限；重启系统界面需要 Root，日常调参无需 Root。"),
    Checking("正在验证…", "请在 Root 管理器的授权弹窗中允许 thunder，最多等待 30 秒。"),
    Granted("已授权", "本次验证已取得 Root 权限，可用于重启系统界面。授权更改后请重新验证。"),
    Denied("未获授权", "未取得 Root 权限，请在 Root 管理器中允许 thunder 后重试。日常调参仍可使用。"),
    Unavailable("Root 不可用", "无法执行 su，请检查设备的 Root 环境和 thunder 授权。日常调参仍可使用。"),
    TimedOut("验证超时", "30 秒内未完成授权，请检查 Root 管理器后重新验证。")
}

/** An explicit, read-only check, owned by the application so rotation cannot duplicate a prompt. */
internal object RootAccess {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val mutableState = MutableStateFlow(RootAccessState.Unchecked)
    val state = mutableState.asStateFlow()

    fun verify() {
        if (!lock.tryLock()) return
        mutableState.value = RootAccessState.Checking
        scope.launch {
            try {
                mutableState.value = RootAccessProbe.check()
            } finally {
                lock.unlock()
            }
        }
    }
}

internal object RootAccessProbe {
    fun check(
        timeoutMillis: Long = 30_000,
        startProcess: () -> Process = {
            ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start()
        }
    ): RootAccessState {
        val process = try {
            startProcess()
        } catch (_: IOException) {
            return RootAccessState.Unavailable
        } catch (_: SecurityException) {
            return RootAccessState.Unavailable
        }
        return try {
            process.outputStream.close()
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                RootAccessState.TimedOut
            } else {
                val output = process.inputStream.use { it.readNBytes(128).toString(Charsets.UTF_8).trim() }
                if (process.exitValue() == 0 && output == "0") RootAccessState.Granted
                else RootAccessState.Denied
            }
        } catch (_: IOException) {
            RootAccessState.Unavailable
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            RootAccessState.Unavailable
        } finally {
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.outputStream.close() }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }
}
