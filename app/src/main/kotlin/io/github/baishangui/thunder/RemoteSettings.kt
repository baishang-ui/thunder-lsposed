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
    @Volatile private var service: XposedService? = null
    var status by mutableStateOf("等待 LSPosed API 102 服务")
        private set
    var scopeStatus by mutableStateOf("作用域：等待 LSPosed 连接")
        private set
    private val recommendedScope = linkedMapOf(
        "com.android.systemui" to "系统界面",
        "com.oplus.systemui.plugins" to "系统界面插件"
    )
    fun refreshScope() {
        val framework = service ?: return
        Thread({ updateScope(framework) }, "Thunder-scope-check").start()
    }
    private fun updateScope(framework: XposedService) {
        scopeStatus = runCatching {
            val actual = framework.scope.toSet()
            val missing = recommendedScope.filterKeys { it !in actual }.values
            if (missing.isEmpty()) "作用域已完整：系统界面、系统界面插件"
            else "作用域缺少：${missing.joinToString("、")}。请在 LSPosed 的 thunder 作用域中勾选，升级不会自动启用新增作用域；勾选后重启手机。"
        }.getOrElse { "无法读取实际作用域：${it.message}。请在 LSPosed 手动确认系统界面和系统界面插件均已勾选。" }
    }
    fun connect(context: Context) {
        val app = context.applicationContext
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(bound: XposedService) {
                if (bound.apiVersion < 102) { status = "框架服务低于 API 102"; return }
                service = bound
                // Binder callback must not do disk IO on the main thread.
                Thread({ publish(app) }, "Thunder-config-sync").start()
            }
            override fun onServiceDied(bound: XposedService) {
                if (service === bound) { service = null; status = "LSPosed 服务已断开，请重新打开应用"; scopeStatus = "作用域：服务已断开" }
            }
        })
    }
    @Synchronized fun publish(context: Context): Boolean {
        val framework = service ?: return false
        updateScope(framework)
        return runCatching {
            val values = SettingsProvider.preferences(context).all
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
