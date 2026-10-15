package io.github.baishangui.thunder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast

/** A real high-importance notification exercises the same heads-up path as other apps. */
internal object TestNotification {
    private const val CHANNEL = "thunder_banner_test_v1"
    private val handler = Handler(Looper.getMainLooper())
    private var pendingDelivery: Runnable? = null
    fun permissionGranted(context: Context) = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    /** Check settings before leaving the activity so permission screens remain interactive. */
    fun prepare(context: Context): String? {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "横幅效果测试", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "点击 thunder 的测试按钮时发送，用于检查通知横幅外观"
            enableVibration(true)
        })
        if (!manager.areNotificationsEnabled()) {
            openSettings(context, false)
            return "通知权限已关闭，请允许 thunder 发送通知后重试"
        }
        if ((manager.getNotificationChannel(CHANNEL)?.importance ?: 0) < NotificationManager.IMPORTANCE_HIGH) {
            openSettings(context, true)
            return "请在此通知类别中开启横幅提醒，然后再次测试"
        }
        return null
    }
    /** Called from Activity.onStop, after ColorOS no longer sees thunder as the foreground app. */
    fun postAfterAppStops(context: Context) {
        val app = context.applicationContext
        pendingDelivery?.let(handler::removeCallbacks)
        val delivery = Runnable {
            pendingDelivery = null
            runCatching { post(app) }.onFailure {
                Toast.makeText(app, "测试通知发送失败，请返回 thunder 检查通知权限与横幅设置", Toast.LENGTH_LONG).show()
            }
        }
        pendingDelivery = delivery
        handler.postDelayed(delivery, 1_000)
    }
    private fun post(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        check(permissionGranted(context) && manager.areNotificationsEnabled()) { "通知权限已关闭" }
        check((manager.getNotificationChannel(CHANNEL)?.importance ?: 0) >= NotificationManager.IMPORTANCE_HIGH) { "横幅提醒已关闭" }
        val intent = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("thunder · 横幅通知测试")
            .setContentText("这是一条测试通知，用于查看横幅模糊、混色与圆角效果。")
            .setContentIntent(intent).setAutoCancel(true).setTimeoutAfter(15_000)
            .setCategory(Notification.CATEGORY_MESSAGE).build()
        // A fresh id can alert again; reusing the same notification can be rate-limited as an update.
        manager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification)
    }
    private fun openSettings(context: Context, channel: Boolean) {
        val intent = Intent(if (channel) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (channel) intent.putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
        context.startActivity(intent)
    }
}
