package com.vatradar.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vatradar.app.MainActivity
import com.vatradar.app.R

object Notifications {

    /** 관제소가 새로 떴을 때 — 소리와 함께 알립니다. */
    const val CHANNEL_ONLINE = "controller_online"

    /** 실시간 감시 중임을 나타내는 상시 알림 — 조용해야 합니다. */
    const val CHANNEL_WATCHING = "controller_watching"

    const val WATCHING_ID = 1001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONLINE,
                context.getString(R.string.channel_controller_online),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_controller_online_desc)
            }
        )

        // 상시 알림은 목록에만 남고 소리·배지를 내지 않도록 낮은 중요도로 둡니다.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WATCHING,
                context.getString(R.string.channel_watching),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_watching_desc)
                setShowBadge(false)
            }
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 포그라운드 서비스가 띄우는 상시 알림. */
    fun buildWatchingNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_WATCHING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.watching_title))
            .setContentText(context.getString(R.string.watching_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(context))
            .build()

    fun showControllerOnline(context: Context, callsigns: List<String>) {
        if (callsigns.isEmpty() || !canPost(context)) return
        ensureChannels(context)

        val title = if (callsigns.size == 1) {
            context.getString(R.string.controller_online_one, callsigns.first())
        } else {
            context.getString(R.string.controller_online_many, callsigns.size)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ONLINE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(callsigns.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(callsigns.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(callsigns.hashCode(), notification)
    }
}
