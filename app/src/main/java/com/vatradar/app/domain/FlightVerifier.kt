package com.vatradar.app.domain

import com.vatradar.app.data.local.ChallengeEntity
import com.vatradar.app.domain.model.Aircraft
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.greatCircleNm

/**
 * 챌린지 완주 판정.
 *
 * VATSIM에는 비행 기록 조회 API가 없습니다(`/members/{cid}/flights`는 404).
 * 알 수 있는 건 **지금 접속 중인 사람**과 **누적 비행시간**뿐이라, 두 가지를 엮어 판정합니다.
 *
 *  1) 실시간 피드에서 내 CID가 해당 구간을 비행 중인 걸 본다 → 진행 중 표시
 *  2) 도착지 근처에 낮은 고도·속도로 있으면 → 완주
 *  3) 진행 중이던 사람이 피드에서 사라졌고 누적 비행시간이 늘었으면 → 완주
 *
 * 3번이 필요한 이유: 착륙 순간을 항상 관찰할 수는 없습니다(앱이 꺼져 있거나 폴링 간격 사이).
 * 접속이 끊긴 뒤 시간이 늘었다는 건 실제로 비행을 마쳤다는 뜻입니다.
 *
 * 이 로직은 순수 함수로 두어 네트워크 없이 검증할 수 있게 했습니다.
 */
object FlightVerifier {

    /** 도착 판정 반경. 공항 상공을 지나쳐 가는 경우와 구분되도록 속도·고도도 함께 봅니다. */
    const val ARRIVAL_RADIUS_NM = 8.0
    const val ARRIVAL_MAX_GROUND_SPEED_KT = 40
    const val ARRIVAL_MAX_ALTITUDE_AGL_FT = 2_000

    /**
     * 비행계획이 챌린지 구간과 일치하는지.
     * 대소문자·공백 차이는 무시합니다.
     */
    fun matchesRoute(aircraft: Aircraft, challenge: ChallengeEntity): Boolean {
        val departure = aircraft.departure?.trim()?.uppercase()
        val arrival = aircraft.arrival?.trim()?.uppercase()
        return departure == challenge.origin.uppercase() &&
            arrival == challenge.destination.uppercase()
    }

    /**
     * 지금 도착했다고 볼 수 있는가.
     * 도착 공항을 모르면(DB에 없으면) 판정할 수 없어 false를 돌려줍니다.
     */
    fun hasArrived(aircraft: Aircraft, arrivalAirport: Airport?): Boolean {
        if (arrivalAirport == null) return false

        val distance = greatCircleNm(
            aircraft.latitude, aircraft.longitude,
            arrivalAirport.latitude, arrivalAirport.longitude
        )
        if (distance > ARRIVAL_RADIUS_NM) return false
        if (aircraft.groundSpeed > ARRIVAL_MAX_GROUND_SPEED_KT) return false

        // 공항 표고를 빼서 지면 기준 고도로 봅니다 (고지대 공항 대응).
        val aboveField = aircraft.altitude - arrivalAirport.elevationFt
        return aboveField <= ARRIVAL_MAX_ALTITUDE_AGL_FT
    }

    /**
     * 접속이 끊긴 뒤 완주로 인정할지.
     *
     * 비행 중인 걸 본 적이 있어야 하고(그래야 남의 비행을 가로채지 못합니다),
     * 누적 비행시간이 의미 있게 늘어야 합니다.
     */
    fun completedAfterDisconnect(
        challenge: ChallengeEntity,
        currentPilotHours: Double?
    ): Boolean {
        if (!challenge.seenEnroute) return false
        val baseline = challenge.baselinePilotHours ?: return false
        val current = currentPilotHours ?: return false
        return current - baseline >= MIN_HOURS_DELTA
    }

    /**
     * 최소 비행시간 증가분.
     *
     * VATSIM 통계는 소수점 둘째 자리까지라 반올림 오차가 있고, 아주 짧은 접속도
     * 시간에 잡힙니다. 0.2시간(12분)을 넘겨야 실제 비행으로 봅니다.
     */
    private const val MIN_HOURS_DELTA = 0.2
}
