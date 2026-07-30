package com.vatradar.app

import com.vatradar.app.data.local.ChallengeEntity
import com.vatradar.app.data.local.ChallengeStatus
import com.vatradar.app.domain.FlightVerifier
import com.vatradar.app.domain.model.Aircraft
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.PilotTier
import com.vatradar.app.domain.model.pointsForDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTest {

    // ---------------- 포인트 / 등급 ----------------

    @Test
    fun `포인트는 거리에 비례한다`() {
        assertEquals(100, pointsForDistance(1_000))
        assertEquals(250, pointsForDistance(2_500))
        assertEquals(600, pointsForDistance(6_000))
    }

    @Test
    fun `아주 짧은 거리도 최소 1점은 준다`() {
        assertEquals(1, pointsForDistance(5))
        assertEquals(1, pointsForDistance(0))
    }

    @Test
    fun `포인트 구간마다 등급이 올라간다`() {
        assertEquals(PilotTier.BRONZE, PilotTier.forPoints(0))
        assertEquals(PilotTier.BRONZE, PilotTier.forPoints(999))
        assertEquals(PilotTier.SILVER, PilotTier.forPoints(1_000))
        assertEquals(PilotTier.GOLD, PilotTier.forPoints(10_000))
        assertEquals(PilotTier.PLATINUM, PilotTier.forPoints(100_000))
        assertEquals(PilotTier.PLATINUM, PilotTier.forPoints(999_999))
    }

    /** 상위로 갈수록 인원이 적어지도록 문턱이 10배씩 벌어져야 합니다. */
    @Test
    fun `등급 간격이 10배씩 벌어진다`() {
        assertEquals(10, PilotTier.GOLD.minPoints / PilotTier.SILVER.minPoints)
        assertEquals(10, PilotTier.PLATINUM.minPoints / PilotTier.GOLD.minPoints)
    }

    @Test
    fun `다음 등급까지의 진행률을 계산한다`() {
        // 실버(1,000) ~ 골드(10,000) 구간의 중간
        assertEquals(0.5f, PilotTier.progressToNext(5_500), 0.01f)
        assertEquals(0f, PilotTier.progressToNext(1_000), 0.01f)
    }

    @Test
    fun `최고 등급에서는 진행률이 가득 찬다`() {
        assertEquals(1f, PilotTier.progressToNext(100_000), 0.001f)
        assertEquals(null, PilotTier.PLATINUM.next)
    }

    // ---------------- 완주 판정 ----------------

    private fun challenge(
        origin: String = "RKSI",
        destination: String = "RJTT",
        seenEnroute: Boolean = false,
        baseline: Double? = 100.0
    ) = ChallengeEntity(
        id = 1, origin = origin, destination = destination,
        distanceNm = 600, points = 60,
        status = ChallengeStatus.ACTIVE, createdAt = 0L,
        seenEnroute = seenEnroute, baselinePilotHours = baseline
    )

    private fun aircraft(
        departure: String? = "RKSI",
        arrival: String? = "RJTT",
        lat: Double = 35.55, lon: Double = 139.78,
        altitude: Int = 100, groundSpeed: Int = 0
    ) = Aircraft(
        cid = 1, callsign = "TEST1", pilotName = "T",
        latitude = lat, longitude = lon, altitude = altitude,
        groundSpeed = groundSpeed, heading = 0f, aircraftType = "B738",
        departure = departure, arrival = arrival, route = null, flightRules = "I",
        plannedDepartureHhmm = null, enrouteTimeHhmm = null
    )

    private val haneda = Airport(
        icao = "RJTT", name = "Haneda", iata = "HND", country = "JP",
        countryName = "Japan", continent = "AS",
        latitude = 35.5533, longitude = 139.7811, elevationFt = 35,
        maxRunwayFt = 11024, hardSurface = true, international = true
    )

    @Test
    fun `비행계획이 챌린지 구간과 맞아야 인정된다`() {
        assertTrue(FlightVerifier.matchesRoute(aircraft(), challenge()))
        assertFalse(FlightVerifier.matchesRoute(aircraft(departure = "RKSS"), challenge()))
        assertFalse(FlightVerifier.matchesRoute(aircraft(arrival = "RJAA"), challenge()))
    }

    @Test
    fun `비행계획이 없으면 인정되지 않는다`() {
        assertFalse(
            FlightVerifier.matchesRoute(aircraft(departure = null, arrival = null), challenge())
        )
    }

    @Test
    fun `도착지 근처에 낮고 느리게 있으면 도착으로 본다`() {
        assertTrue(FlightVerifier.hasArrived(aircraft(), haneda))
    }

    @Test
    fun `상공을 순항으로 지나가는 것은 도착이 아니다`() {
        val overflying = aircraft(altitude = 35_000, groundSpeed = 450)
        assertFalse(FlightVerifier.hasArrived(overflying, haneda))
    }

    @Test
    fun `도착지에서 멀면 도착이 아니다`() {
        // 인천 상공에 낮게 있어도 도착지는 하네다입니다
        val elsewhere = aircraft(lat = 37.46, lon = 126.44)
        assertFalse(FlightVerifier.hasArrived(elsewhere, haneda))
    }

    @Test
    fun `공항 정보를 모르면 도착 판정을 하지 않는다`() {
        assertFalse(FlightVerifier.hasArrived(aircraft(), null))
    }

    /** 착륙 순간을 못 봤을 때의 대비책. 접속 종료 + 비행시간 증가로 인정합니다. */
    @Test
    fun `비행 중인 걸 본 뒤 시간이 늘면 완주로 인정한다`() {
        assertTrue(
            FlightVerifier.completedAfterDisconnect(
                challenge(seenEnroute = true, baseline = 100.0),
                currentPilotHours = 101.5
            )
        )
    }

    @Test
    fun `비행 중인 걸 본 적 없으면 시간이 늘어도 인정하지 않는다`() {
        // 다른 비행으로 시간이 늘었을 뿐일 수 있습니다
        assertFalse(
            FlightVerifier.completedAfterDisconnect(
                challenge(seenEnroute = false, baseline = 100.0),
                currentPilotHours = 105.0
            )
        )
    }

    @Test
    fun `시간 증가가 미미하면 인정하지 않는다`() {
        // 접속만 했다 끊은 경우
        assertFalse(
            FlightVerifier.completedAfterDisconnect(
                challenge(seenEnroute = true, baseline = 100.0),
                currentPilotHours = 100.05
            )
        )
    }

    @Test
    fun `기준 시간이나 현재 시간을 모르면 인정하지 않는다`() {
        assertFalse(
            FlightVerifier.completedAfterDisconnect(
                challenge(seenEnroute = true, baseline = null), 105.0
            )
        )
        assertFalse(
            FlightVerifier.completedAfterDisconnect(
                challenge(seenEnroute = true, baseline = 100.0), null
            )
        )
    }
}
