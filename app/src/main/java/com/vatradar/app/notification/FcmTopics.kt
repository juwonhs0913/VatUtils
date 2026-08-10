package com.vatradar.app.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

/**
 * 관심 콜사인 하나당 FCM 토픽 하나를 구독합니다 (server/functions/index.js와 규칙 공유).
 *
 * google-services.json이 없으면 FirebaseApp이 초기화되지 않아 여기서 예외가 납니다.
 * 그건 정상적인 미설정 상태이므로 로그만 남기고 넘어갑니다 — 이 경우
 * ControllerWatchWorker의 주기 폴링이 알림을 담당합니다.
 */
object FcmTopics {

    private const val PREFIX = "cs_"

    /**
     * 내 CID 토픽. 서버가 챌린지 완주를 알릴 때 씁니다.
     * 토픽으로 보내면 서버가 FCM 토큰을 저장할 필요가 없습니다.
     */
    fun subscribeCid(cid: String) = runGuarded("CID 구독") {
        if (cid.isNotBlank()) {
            FirebaseMessaging.getInstance().subscribeToTopic("cid_" + cid.trim())
        }
    }

    fun unsubscribeCid(cid: String) = runGuarded("CID 구독 해제") {
        if (cid.isNotBlank()) {
            FirebaseMessaging.getInstance().unsubscribeFromTopic("cid_" + cid.trim())
        }
    }

    fun subscribe(callsign: String) = runGuarded("구독") {
        FirebaseMessaging.getInstance().subscribeToTopic(PREFIX + normalize(callsign))
    }

    fun unsubscribe(callsign: String) = runGuarded("구독 해제") {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(PREFIX + normalize(callsign))
    }

    /** 토픽 이름은 [a-zA-Z0-9-_.~%]만 허용되므로 그 외 문자는 밑줄로 바꿉니다. */
    private fun normalize(callsign: String): String =
        callsign.trim().uppercase().replace(Regex("[^A-Z0-9_.~%-]"), "_")

    private inline fun runGuarded(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.d("VATFlight", "FCM 토픽 $action 생략 (Firebase 미설정): ${e.message}")
        }
    }
}
