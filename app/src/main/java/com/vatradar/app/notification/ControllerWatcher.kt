package com.vatradar.app.notification

import android.content.Context
import android.util.Log
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.domain.CallsignMatcher

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

        // 알림과 무관하게, 15분마다 도는 이 기회에 챌린지 완주도 확인합니다.
        // 비행 중 앱이 한 번도 열리지 않아도 완주가 잡히도록 하기 위함입니다.
        if (user.vatsimCid.isNotBlank()) {
            runCatching {
                ServiceLocator.flightProgressRepository(context).sync(user.vatsimCid)
            }.onFailure { Log.w("VATRadar", "챌린지 확인 실패", it) }
        }

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

    /**
     * FCM 푸시로 받은 "새로 접속" 목록을 알립니다.
     *
     * 15분 폴링과 **같은 기록(alreadyNotified)을 공유**하는 게 핵심입니다.
     * 두 경로를 모두 살려두는 이유는 서버가 죽어도 알림이 끊기지 않게 하기 위함이고,
     * 그 대가로 같은 관제소를 두 번 알릴 위험이 생깁니다. 여기서 그걸 막습니다.
     *
     * 폴링은 현재 접속자 전체를 알기 때문에 기록을 통째로 덮어쓰지만,
     * 푸시는 새로 뜬 것만 알기 때문에 기록에 **더하기**만 합니다.
     */
    suspend fun notifyFromPush(context: Context, incoming: List<String>) {
        val settings = ServiceLocator.settingsRepository(context)
        val user = settings.current()
        if (!user.notifyEnabled) return

        val already = settings.alreadyNotified()
        val fresh = selectNewCallsigns(incoming, user.watchedCallsigns, already)
        if (fresh.isEmpty()) return

        Notifications.showControllerOnline(context, fresh)
        settings.setAlreadyNotified(already + fresh)
        Log.d("VATRadar", "푸시 알림: ${fresh.joinToString(", ")}")
    }

    /**
     * 푸시로 들어온 콜사인 중 실제로 알려야 할 것만 고릅니다.
     *
     * 두 가지를 걸러냅니다.
     *  - 이미 알린 것 (폴링이 먼저 알렸을 수 있음)
     *  - 사용자가 등록하지 않은 것 (관제소를 지웠는데 토픽 구독 해제가 실패했을 수 있음)
     */
    fun selectNewCallsigns(
        incoming: List<String>,
        watched: Set<String>,
        alreadyNotified: Set<String>
    ): List<String> {
        val targets = watched.map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        if (targets.isEmpty()) return emptyList()

        return incoming
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { callsign -> CallsignMatcher.matchesAny(callsign, targets) }
            .filterNot { it in alreadyNotified }
            .sorted()
    }
}
