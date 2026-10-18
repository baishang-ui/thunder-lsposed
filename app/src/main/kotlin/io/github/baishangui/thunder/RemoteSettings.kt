package io.github.baishangui.thunder

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/** App writes through the framework; SystemUI reads before Application.attach. */
internal object RemoteSettings {
    enum class Activation(val label: String, val hint: String) {
        Waiting("待激活 / 待连接", "请在 LSPosed 中启用 thunder，勾选系统界面和系统界面插件作用域，再重新打开应用。"),
        Connected("已激活", "已连接 LSPosed 框架服务。首次激活或更改作用域后，请正常重启手机加载模块。"),
        Unsupported("框架版本不支持", "需要支持 API 102 的 LSPosed 框架，请更新框架后重新打开应用。"),
        Disconnected("连接已断开", "请在 LSPosed 中确认模块已启用，再重新打开应用检查。")
    }
    @Volatile private var service: XposedService? = null
    var activation by mutableStateOf(Activation.Waiting)
        private set
    var status by mutableStateOf("等待 LSPosed API 102 服务")
        private set
    var scopeStatus by mutableStateOf("作用域：等待 LSPosed 连接")
        private set
    fun refreshScope() {
        val framework = service ?: return
        Thread({ updateScope(framework) }, "Thunder-scope-check").start()
    }
    private fun updateScope(framework: XposedService) {
        scopeStatus = runCatching {
            ModuleScope.describe(framework.scope.toSet())
        }.getOrElse { "无法读取实际作用域：${it.message}。请在 LSPosed 手动确认系统界面、系统界面插件。" }
    }
    fun connect(context: Context) {
        val app = context.applicationContext
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(bound: XposedService) {
                if (bound.apiVersion < 102) {
                    service = null
                    activation = Activation.Unsupported
                    status = "框架服务低于 API 102"
                    return
                }
                service = bound
                activation = Activation.Connected
                // Binder callback must not do disk IO on the main thread.
                Thread({ publish(app) }, "Thunder-config-sync").start()
            }
            override fun onServiceDied(bound: XposedService) {
                if (service === bound) {
                    service = null
                    activation = Activation.Disconnected
                    status = "LSPosed 服务已断开，请重新打开应用"
                    scopeStatus = "作用域：服务已断开"
                }
            }
        })
    }
    @Synchronized fun publish(context: Context): Boolean {
        val framework = service ?: return false
        return runCatching {
            val values = SettingsProvider.preferences(context).all
            updateScope(framework)
            val bundle = Bundle().apply { values.forEach { (k,v) -> when(v) {
                is Boolean -> putBoolean(k,v); is Int -> putInt(k,v)
            } } }
            val snapshot = Settings.from(bundle).bundle()
            val revision = values["revision"] as? Long ?: 0L
            val edit = framework.getRemotePreferences("settings").edit().clear()
            snapshot.keySet().forEach { key -> @Suppress("DEPRECATION") when(val v=snapshot.get(key)) {
                is Boolean -> edit.putBoolean(key,v); is Int -> edit.putInt(key,v)
            } }
            edit.putLong("revision", revision)
            check(edit.commit()) { "远程配置写入失败" }
            status = "${framework.frameworkName} ${framework.frameworkVersion} · API ${framework.apiVersion} 已连接\n配置 $revision 已同步"
            true
        }.getOrElse { status = "远程配置同步失败：${it.message}"; false }
    }
}

class ThunderApplication : Application() {
    override fun onCreate() { super.onCreate(); RemoteSettings.connect(this) }
}
