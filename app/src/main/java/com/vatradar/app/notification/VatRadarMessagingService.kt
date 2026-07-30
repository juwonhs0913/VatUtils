package com.vatradar.app.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * FCM 수신부 (F4의 즉시 알림 경로).
 *
 * google-services.json이 없으면 Firebase가 초기화되지 않아 이 서비스는 호출되지 않습니다.
 * 그 경우 앱은 ControllerWatchWorker의 15분 폴링으로 동작합니다.
 *
 * 서버(server/functions/index.js)는 다음 형태의 data 메시지를 보냅니다:
 *   { "callsigns": "RKSI_TWR,RJJJ_CTR" }
 */
class VatRadarMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // 서버가 토큰별로 관심 키워드를 관리하려면 여기서 등록 API를 호출합니다.
        // 현재 구현은 토픽 구독 방식이라 별도 업로드가 필요 없습니다.
        Log.d("VATRadar", "FCM 토큰 갱신됨")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val callsigns = message.data["callsigns"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: return

        // 이 콜백은 이미 백그라운드 스레드에서 돌고, 시스템이 주는 실행 시간은 짧습니다.
        // DataStore 조회가 걸리는 상황을 대비해 시간 제한을 둡니다.
        runBlocking {
            runCatching {
                withTimeout(PROCESS_TIMEOUT_MS) {
                    ControllerWatcher.notifyFromPush(applicationContext, callsigns)
                }
            }.onFailure { Log.w("VATRadar", "푸시 처리 실패", it) }
        }
    }

    private companion object {
        const val PROCESS_TIMEOUT_MS = 8_000L
    }
}
