package com.vatradar.app.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

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
            ?: message.notification?.body?.let { listOf(it) }
            ?: return

        Notifications.showControllerOnline(applicationContext, callsigns)
    }
}
