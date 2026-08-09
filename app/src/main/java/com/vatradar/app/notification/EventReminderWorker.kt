package com.vatradar.app.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 관심 이벤트 시작 1시간 전 알림.
 *
 * AlarmManager 대신 WorkManager를 쓰는 이유: Android 12부터 정확한 알람은 별도
 * 권한(SCHEDULE_EXACT_ALARM)이 필요하고 심사에서도 사유를 요구합니다. 이벤트
 * 알림은 몇 분 어긋나도 쓸모가 줄지 않으므로 권한 없이 되는 쪽을 씁니다.
 */
class EventReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val name = inputData.getString(KEY_NAME) ?: return Result.success()
        val startsAt = inputData.getLong(KEY_STARTS_AT, 0L)
        Notifications.showEventReminder(applicationContext, name, startsAt)
        return Result.success()
    }

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_STARTS_AT = "startsAt"
        private const val LEAD_MILLIS = 60 * 60 * 1000L

        private fun workName(eventId: Int) = "event_reminder_$eventId"

        /**
         * 예약합니다. 이미 시작 1시간 안쪽이면 아무것도 하지 않습니다 —
         * 지난 일을 뒤늦게 알리는 건 방해만 됩니다.
         */
        fun schedule(context: Context, eventId: Int, name: String, startEpochMillis: Long) {
            val delay = startEpochMillis - LEAD_MILLIS - System.currentTimeMillis()
            if (delay <= 0) return

            val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_NAME, name)
                        .putLong(KEY_STARTS_AT, startEpochMillis)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(eventId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, eventId: Int) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(eventId))
        }
    }
}
