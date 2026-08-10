package com.vatradar.app

import com.vatradar.app.domain.ApproachDirectory
import com.vatradar.app.domain.model.Airport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 콜사인 앞머리를 되짚어 어느 나라 접근관제석인지 가리는 규칙.
 *
 * 앞머리에 쓰는 코드가 나라마다 달라서, 규칙 하나만으로는 나라 하나가 통째로 빠집니다.
 * 실제로 ICAO만 보던 동안 미국은 접근관제석이 하나도 뜨지 않았습니다 — 전부 IATA 표기라서요.
 */
class ApproachDirectoryTest {

    private fun airport(icao: String, iata: String, country: String, name: String) = Airport(
        icao = icao,
        name = name,
        iata = iata,
        country = country,
        countryName = country,
        continent = "AS",
        latitude = 0.0,
        longitude = 0.0,
        elevationFt = 0,
        maxRunwayFt = 10000,
        hardSurface = true,
        international = true
    )

    private val korea = listOf(
        airport("RKSI", "ICN", "KR", "Incheon"),
        airport("RKSS", "GMP", "KR", "Gimpo")
    )

    private val us = listOf(airport("KATL", "ATL", "US", "Hartsfield-Jackson"))

    private val prefixes = mapOf("RK" to "KR", "K" to "US", "EK" to "DK", "Y" to "AU")

    @Test
    fun `ICAO 앞머리는 그 공항 이름으로 나온다`() {
        val found = ApproachDirectory.candidatesFor(
            observed = setOf("RKSS_APP", "RKSI_TWR"),
            country = "KR",
            airports = korea,
            icaoPrefixes = prefixes
        )

        assertEquals(listOf("RKSS_APP"), found.map { it.callsign })
        assertEquals("Gimpo", found.single().servedBy)
    }

    /** 인천 어프로치는 존재하지 않습니다. 목록에 지어내 넣지 않는지 확인합니다. */
    @Test
    fun `없는 자리는 만들어내지 않는다`() {
        val found = ApproachDirectory.candidatesFor(
            observed = setOf("RKSS_APP"),
            country = "KR",
            airports = korea,
            icaoPrefixes = prefixes
        )

        assertTrue(found.none { it.callsign == "RKSI_APP" })
    }

    @Test
    fun `미주 콜사인은 IATA로 되짚는다`() {
        val found = ApproachDirectory.candidatesFor(
            observed = setOf("ATL_APP", "ATL_DEP"),
            country = "US",
            airports = us,
            icaoPrefixes = prefixes
        )

        assertEquals(listOf("ATL_APP", "ATL_DEP"), found.map { it.callsign })
        assertEquals("Hartsfield-Jackson", found.first().servedBy)
    }

    /** EKDK는 공항이 아니라 덴마크 FIR 코드입니다. 접두사로 나라를 봅니다. */
    @Test
    fun `공항이 아닌 네 글자는 접두사로 나라를 본다`() {
        val found = ApproachDirectory.candidatesFor(
            observed = setOf("EKDK_APP"),
            country = "DK",
            airports = emptyList(),
            icaoPrefixes = prefixes
        )

        assertEquals(listOf("EKDK_APP"), found.map { it.callsign })
    }

    /** 호주 VATPAC은 두 글자 약어를 씁니다. 데이터로 풀리지 않아 표로 적어 둔 것들. */
    @Test
    fun `공항 코드가 아닌 약어는 표에서 찾는다`() {
        val found = ApproachDirectory.candidatesFor(
            observed = setOf("BN_APP", "SY-D_APP", "PCT_APP"),
            country = "AU",
            airports = emptyList(),
            icaoPrefixes = prefixes
        )

        assertEquals(listOf("BN_APP", "SY-D_APP"), found.map { it.callsign })
        assertEquals("Sydney", found.last().servedBy)
    }

    @Test
    fun `다른 나라 자리는 섞여 들어오지 않는다`() {
        val found = ApproachDirectory.candidatesFor(
            observed = setOf("ATL_APP", "RKSS_APP", "BN_APP"),
            country = "KR",
            airports = korea,
            icaoPrefixes = prefixes
        )

        assertEquals(listOf("RKSS_APP"), found.map { it.callsign })
    }

    @Test
    fun `모아 둔 목록이 없으면 아무것도 보여 주지 않는다`() {
        val found = ApproachDirectory.candidatesFor(
            observed = emptySet(),
            country = "KR",
            airports = korea,
            icaoPrefixes = prefixes
        )

        assertTrue(found.isEmpty())
    }
}
