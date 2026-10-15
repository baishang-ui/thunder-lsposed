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
                if (service === bound) { service = null; status = "LSPosed 服务已断开，请重新打开应用" }
            }
        })
    }
    @Synchronized fun publish(context: Context): Boolean {
        val framework = service ?: return false
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
