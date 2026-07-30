package com.vatradar.app.notification

import android.content.Context
import android.util.Log
import com.vatradar.app.R
import com.vatradar.app.di.ServiceLocator

/**
 * 서버(Cloudflare Worker)가 완주를 알려왔을 때 포인트를 지급하고 알립니다.
 *
 * 서버는 챌린지 ID만 보냅니다. 포인트 계산과 등급 판정은 앱이 하므로,
 * 서버가 조작되더라도 존재하지 않는 챌린지 ID로는 아무 일도 일어나지 않습니다.
 */
object ChallengeCompletionHandler {

    suspend fun handle(context: Context, challengeId: Long) {
        val challenges = ServiceLocator.challengeRepository(context)
        val completed = challenges.completeById(challengeId)

        if (completed == null) {
            // 이미 기기 판정으로 처리했거나 만료된 챌린지입니다. 중복 지급을 막습니다.
            Log.d("VATRadar", "완주 푸시 무시 (해당 챌린지 없음): $challengeId")
            return
        }

        Notifications.showChallengeComplete(
            context,
            "${completed.origin} → ${completed.destination}",
            completed.points
        )

        val user = ServiceLocator.settingsRepository(context).current()
        challenges.unregisterWatch(user.vatsimCid, user.vatsimLinkToken, challengeId)

        Log.d("VATRadar", "챌린지 완주 처리: ${completed.origin}→${completed.destination} +${completed.points}점")
    }
}
