package com.vatradar.app.domain

import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.distanceNmTo

/**
 * 같은 접근관제를 받는 이웃 공항.
 *
 * 인천(RKSI)과 김포(RKSS)는 **서울 어프로치** 하나가 봅니다. 하네다(RJTT)와
 * 나리타(RJAA)도 도쿄 어프로치 하나입니다. 그런데 관제사는 둘 중 한쪽 콜사인으로만
 * 접속하므로, RKSI만 등록해 둔 사람은 RKSS_APP이 떠도 알림을 못 받습니다.
 *
 * VATSIM 데이터에 "이 어프로치가 어느 공항들을 담당하는가"는 없습니다. 대신
 * 거리로 가릅니다 — 한 TMA 안에 있는 공항들은 서로 가깝습니다.
 *
 *   RKSI-RKSS 17nm, KJFK-KEWR 18nm, LFPG-LFPO 18nm, RJTT-RJAA 32nm  (같은 TMA)
 *   EDDF-EDDK 74nm                                                   (다른 TMA)
 *
 * 40해리로 자르면 위 경우가 정확히 갈립니다.
 *
 * 타워·그라운드·딜리버리는 공항마다 따로이므로 **어프로치와 디파처만** 넓힙니다.
 * 김포 타워까지 알려 주면 등록하지도 않은 공항의 알림이 됩니다.
 */
object ApproachNeighbours {

    /** 같은 접근관제 구역으로 볼 최대 거리. */
    const val RADIUS_NM = 40.0

    /** 넓혀서 받을 시설. */
    private val FACILITIES = listOf("APP", "DEP")

    /**
     * [code]로 등록했을 때 함께 지켜볼 콜사인들.
     * 예) RKSI → [RKSS_APP, RKSS_DEP]
     *
     * 등록값이 공항 코드가 아니거나(예: RKRR_CTR) 이웃이 없으면 빈 목록입니다.
     */
    fun extraCallsigns(code: String, airports: List<Airport>): List<String> {
        val origin = airportFor(code, airports) ?: return emptyList()

        return airports
            .filter { it.icao != origin.icao && origin.distanceNmTo(it) <= RADIUS_NM }
            .flatMap { neighbour -> FACILITIES.map { "${neighbour.icao}_$it" } }
    }

    /**
     * 등록값이 가리키는 공항.
     *
     * 사용자는 `RKSI`로도 `RKSI_TWR`로도 등록하므로 앞 토큰만 봅니다.
     * 4글자가 아니면 공항이 아니라 FIR 코드로 보고 넘어갑니다.
     */
    private fun airportFor(code: String, airports: List<Airport>): Airport? {
        val icao = code.trim().uppercase().substringBefore('_')
        if (icao.length != 4) return null
        return airports.firstOrNull { it.icao == icao }
    }
}
