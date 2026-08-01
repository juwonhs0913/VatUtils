package com.vatradar.app

import com.vatradar.app.data.local.ChallengeEntity
import com.vatradar.app.data.local.ChallengeStatus
import com.vatradar.app.domain.CallsignMatcher
import com.vatradar.app.domain.FlightVerifier
import com.vatradar.app.domain.model.Aircraft
import com.vatradar.app.domain.model.Airport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTest {

    // ---------------- 콜사인 매칭 ----------------

    /** 사용자가 겪은 문제: RKRR_CTR로 등록했는데 RKRR_A_CTR로 접속해 알림이 안 왔습니다. */
    @Test
    fun `섹터로 나뉜 콜사인도 같은 관제소로 인정한다`() {
        assertTrue(CallsignMatcher.matches("RKRR_A_CTR", "RKRR_CTR"))
        assertTrue(CallsignMatcher.matches("RKRR_N_CTR", "RKRR_CTR"))
        assertTrue(CallsignMatcher.matches("LON_S_CTR", "LON_CTR"))
        assertTrue(CallsignMatcher.matches("ZLA_37_CTR", "ZLA_CTR"))
    }

    @Test
    fun `정확히 같은 콜사인은 당연히 인정한다`() {
        assertTrue(CallsignMatcher.matches("RKSI_TWR", "RKSI_TWR"))
        assertTrue(CallsignMatcher.matches("rksi_twr", "RKSI_TWR"))
    }

    /** 공항 코드만 등록하면 그 공항의 모든 관제석이 잡혀야 합니다. */
    @Test
    fun `공항 코드는 하위 관제석을 모두 포함한다`() {
        listOf("RKSI_TWR", "RKSI_GND", "RKSI_DEL", "RKSI_A_APP").forEach {
            assertTrue(it, CallsignMatcher.matches(it, "RKSI"))
        }
    }

    @Test
    fun `다른 관제소는 잡히지 않는다`() {
        assertFalse(CallsignMatcher.matches("RKSS_TWR", "RKSI"))
        assertFalse(CallsignMatcher.matches("RKRR_A_CTR", "RKSI_CTR"))
        // 접두사가 겹쳐도 자리가 다르면 아닙니다
        assertFalse(CallsignMatcher.matches("RKSI_TWR", "RKSI_GND"))
    }

    @Test
    fun `빈 등록값은 아무것도 잡지 않는다`() {
        assertFalse(CallsignMatcher.matches("RKSI_TWR", ""))
        assertFalse(CallsignMatcher.matches("RKSI_TWR", "   "))
    }

    @Test
    fun `별칭은 원본과 접두사와 축약형을 포함한다`() {
        assertEquals(
            setOf("RKRR_A_CTR", "RKRR", "RKRR_CTR"),
            CallsignMatcher.aliasesFor("RKRR_A_CTR")
        )
        assertEquals(setOf("RKSI_TWR", "RKSI"), CallsignMatcher.aliasesFor("RKSI_TWR"))
    }

    // ---------------- 완주 판정 ----------------

    private fun challenge(
        origin: String = "RKSI",
        destination: String = "RJTT",
        seenEnroute: Boolean = false,
        baseline: Double? = 100.0
    ) = ChallengeEntity(
        id = 1, origin = origin, destination = destination,
        distanceNm = 600,
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
        assertFalse(
            FlightVerifier.completedAfterDisconnect(
                challenge(seenEnroute = false, baseline = 100.0),
                currentPilotHours = 105.0
            )
        )
    }

    @Test
    fun `시간 증가가 미미하면 인정하지 않는다`() {
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
