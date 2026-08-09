package com.vatradar.app.notification

import android.Manifest
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



    /** 관심 이벤트 시작 1시간 전 알림. */
    fun showEventReminder(context: Context, name: String, startEpochMillis: Long) {
        if (!canPost(context)) return
        ensureChannels(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ONLINE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.event_starting_soon))
            .setContentText(name)
            .setStyle(NotificationCompat.BigTextStyle().bigText(name))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        NotificationManagerCompat.from(context)
            .notify(("event" + startEpochMillis + name).hashCode(), notification)
    }

    /** 챌린지 완주 축하 알림. */
    fun showChallengeComplete(context: Context, route: String) {
        if (!canPost(context)) return
        ensureChannels(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ONLINE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.challenge_done))
            .setContentText(route)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(route.hashCode(), notification)
    }

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
