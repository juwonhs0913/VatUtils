package com.vatradar.app.domain.model

import androidx.annotation.StringRes
import com.vatradar.app.R

/**
 * 조종사 등급.
 *
 * 앱에서 뽑은 경로를 완주해 쌓은 포인트로만 정해집니다.
 * VATSIM 전체 비행시간은 반영하지 않습니다 — 앱 활동을 보상하는 게 목적이기 때문입니다.
 *
 * 문턱값은 원래 구상하신 "100시간 / 1,000시간 / 10,000시간"의 **1 : 10 : 100 비율**을
 * 포인트로 옮긴 것입니다. 시간을 그대로 쓰면 하루 5회 제한 때문에 플래티넘은
 * 사실상 도달할 수 없습니다.
 *
 * 감을 잡자면(중거리 1회 ≈ 250점, 하루 평균 2회 비행 기준):
 *   실버      1,000점 — 약 이틀
 *   골드     10,000점 — 약 3주
 *   플래티넘 100,000점 — 약 7개월
 */
enum class PilotTier(
    val minPoints: Int,
    @StringRes val labelRes: Int,
    /** 등급 색. 지도의 내 항공기 아이콘과 배지에 함께 씁니다. */
    val colorArgb: Int
) {
    BRONZE(0, R.string.tier_bronze, 0xFFA9714B.toInt()),
    SILVER(1_000, R.string.tier_silver, 0xFF9AA5B1.toInt()),
    GOLD(10_000, R.string.tier_gold, 0xFFE0A526.toInt()),
    PLATINUM(100_000, R.string.tier_platinum, 0xFF5BC8D6.toInt());

    /** 다음 등급. 최고 등급이면 null. */
    val next: PilotTier? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun forPoints(points: Int): PilotTier =
            entries.last { points >= it.minPoints }

        /**
         * 다음 등급까지의 진행률 (0f~1f). 최고 등급이면 1f.
         * 등급 간 간격이 10배씩 벌어지므로 현재 등급 구간 안에서의 비율로 계산합니다.
         */
        fun progressToNext(points: Int): Float {
            val tier = forPoints(points)
            val next = tier.next ?: return 1f
            val span = (next.minPoints - tier.minPoints).toFloat()
            return ((points - tier.minPoints) / span).coerceIn(0f, 1f)
        }
    }
}

/**
 * 완주 보상 포인트. 거리에 비례합니다.
 *
 * 10해리당 1점 — 단거리 약 100점, 중거리 약 250점, 장거리 600점 이상이 됩니다.
 * 먼 거리를 날수록 더 많이 받되, 배율을 두지 않아 계산이 눈에 보입니다.
 */
fun pointsForDistance(distanceNm: Int): Int = (distanceNm / 10).coerceAtLeast(1)
