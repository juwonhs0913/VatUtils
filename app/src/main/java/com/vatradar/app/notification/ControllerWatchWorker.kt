package com.vatradar.app.notification

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vatradar.app.di.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * 관심 관제소 접속 감지 (F4).
 *
 * 이 Worker는 **서버 없이 동작하는 기본 경로**입니다.
 * Android의 Doze 제약 때문에 주기형 작업의 최소 간격은 15분이고 실제로는 더 지연될 수 있어,
 * 즉시성이 필요하면 FCM 경로(server/functions)를 함께 배포해야 합니다.
 * FCM이 설정되면 서버가 훨씬 짧은 주기로 감지해 푸시를 보내고,
 * 이 Worker는 백업으로 남습니다.
 */
class ControllerWatchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = ServiceLocator.settingsRepository(applicationContext)
        val user = settings.current()

        if (!user.notifyEnabled || user.watchedCallsigns.isEmpty()) return Result.success()

        return try {
            val online = ServiceLocator
                .vatsimRepository(applicationContext)
                .onlineCallsignsMatching(user.watchedCallsigns)
                .toSet()

            val previouslyNotified = settings.alreadyNotified()

            // 이번에 새로 뜬 것만 알립니다. 접속이 유지되는 동안 15분마다 울리면 안 되니까요.
            val newlyOnline = online - previouslyNotified
            if (newlyOnline.isNotEmpty()) {
                Notifications.showControllerOnline(applicationContext, newlyOnline.sorted())
            }

            // 접속 종료된 콜사인은 기록에서 지워 다음 접속 때 다시 알림이 가게 합니다.
            settings.setAlreadyNotified(online)

            Result.success()
        } catch (e: Exception) {
            Log.w("VATRadar", "관제소 감시 실패", e)
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "controller_watch"

        fun enable(context: Context) {
            val request = PeriodicWorkRequestBuilder<ControllerWatchWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun disable(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
