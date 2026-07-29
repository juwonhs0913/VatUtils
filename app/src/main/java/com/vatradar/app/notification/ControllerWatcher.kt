package com.vatradar.app.notification

import android.content.Context
import android.util.Log
import com.vatradar.app.di.ServiceLocator

/**
 * "관심 관제소가 새로 떴는지" 한 번 확인하고 알리는 로직.
 *
 * 15분 폴링(WorkManager)과 60초 감시(포그라운드 서비스)가 같은 판정을 써야 하므로
 * 여기 한 곳에만 둡니다.
 */
object ControllerWatcher {

    /** @return 이번에 새로 알린 콜사인. 알릴 게 없었으면 빈 목록. */
    suspend fun checkOnce(context: Context): List<String> {
        val settings = ServiceLocator.settingsRepository(context)
        val user = settings.current()

        if (!user.notifyEnabled || user.watchedCallsigns.isEmpty()) return emptyList()

        val online = ServiceLocator
            .vatsimRepository(context)
            .onlineCallsignsMatching(user.watchedCallsigns)
            .toSet()

        val previouslyNotified = settings.alreadyNotified()

        // 이번에 새로 뜬 것만 알립니다.
        // 접속이 유지되는 동안 매번 울리면 안 되니까요.
        val newlyOnline = (online - previouslyNotified).sorted()
        if (newlyOnline.isNotEmpty()) {
            Notifications.showControllerOnline(context, newlyOnline)
            Log.d("VATRadar", "새로 접속한 관제소 알림: ${newlyOnline.joinToString(", ")}")
        }

        // 접속 종료된 콜사인은 기록에서 지워 다음 접속 때 다시 알림이 가게 합니다.
        settings.setAlreadyNotified(online)

        return newlyOnline
    }
}
