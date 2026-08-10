package com.vatradar.app

import com.vatradar.app.domain.WatchedStations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 등록 목록을 센터/공항으로 가르고, 공항의 자리를 켜고 끄는 규칙.
 *
 * 저장 형식이 문자열 집합 하나라서 뜻이 겹치는 경우가 생깁니다 — `RKSI`(전부)와
 * `RKSI_TWR`(관제탑만)이 동시에 들어 있을 수 있고, 같은 자리가 `EGLL_APP`과
 * `EGLL_N_APP` 두 모양으로 저장될 수도 있습니다. 그 경계를 여기서 못 박습니다.
 */
class WatchedStationsTest {

    @Test
    fun `센터와 공항과 그 밖을 갈라 놓는다`() {
        val groups = WatchedStations.group(
            setOf("RKRR_CTR", "RKSI", "RKSS_APP", "LON", "SCT_APP", "ANC_FSS")
        )

        assertEquals(listOf("ANC_FSS", "RKRR_CTR"), groups.centers)
        assertEquals(listOf("RKSI", "RKSS"), groups.airports.map { it.icao })
        assertEquals(listOf("LON", "SCT_APP"), groups.others)
    }

    @Test
    fun `맨 ICAO는 모든 자리를 뜻한다`() {
        val airport = WatchedStations.group(setOf("RKSI")).airports.single()

        assertTrue(airport.all)
        assertEquals(listOf("DEL", "GND", "TWR"), airport.boxes)
        assertTrue(airport.isOn("TWR"))
        assertTrue(airport.isOn("DEL"))
    }

    @Test
    fun `개별 등록은 등록한 자리만 켜진다`() {
        val airport = WatchedStations.group(setOf("RKSI_TWR", "RKSI_GND")).airports.single()

        assertFalse(airport.all)
        assertTrue(airport.isOn("TWR"))
        assertFalse(airport.isOn("DEL"))
    }

    @Test
    fun `등록해 둔 접근관제석은 상자로 함께 나온다`() {
        val airport = WatchedStations.group(setOf("RKSS_APP")).airports.single()

        assertEquals(listOf("DEL", "GND", "TWR", "APP"), airport.boxes)
        assertTrue(airport.isOn("APP"))
        assertFalse(airport.isOn("TWR"))
    }

    @Test
    fun `전부 켜진 상태에서 하나를 끄면 나머지를 개별로 적는다`() {
        val change = WatchedStations.togglePosition(setOf("RKSI"), "RKSI", "TWR", on = false)

        assertEquals(setOf("RKSI_DEL", "RKSI_GND"), change.add)
        assertEquals(setOf("RKSI"), change.remove)
    }

    /** `RKSI`와 `RKSI_TWR`이 함께 있을 때 관제탑을 끄면 둘 다 사라져야 합니다. */
    @Test
    fun `겹쳐 저장된 항목도 함께 지운다`() {
        val change = WatchedStations.togglePosition(
            setOf("RKSI", "RKSI_TWR"), "RKSI", "TWR", on = false
        )

        assertEquals(setOf("RKSI_DEL", "RKSI_GND"), change.add)
        assertEquals(setOf("RKSI", "RKSI_TWR"), change.remove)
    }

    /** 섹터가 나뉜 표기(EGLL_N_APP)도 같은 자리로 봅니다. */
    @Test
    fun `모양이 다른 같은 자리도 함께 지운다`() {
        val change = WatchedStations.togglePosition(
            setOf("EGLL_APP", "EGLL_N_APP", "EGLL_TWR"), "EGLL", "APP", on = false
        )

        assertTrue(change.add.isEmpty())
        assertEquals(setOf("EGLL_APP", "EGLL_N_APP"), change.remove)
    }

    @Test
    fun `켜져 있는 자리를 또 켜면 아무 일도 없다`() {
        val change = WatchedStations.togglePosition(setOf("RKSI"), "RKSI", "TWR", on = true)

        assertTrue(change.add.isEmpty())
        assertTrue(change.remove.isEmpty())
    }

    @Test
    fun `꺼져 있던 자리를 켜면 그 항목만 더한다`() {
        val change = WatchedStations.togglePosition(setOf("RKSI_TWR"), "RKSI", "DEL", on = true)

        assertEquals(setOf("RKSI_DEL"), change.add)
        assertTrue(change.remove.isEmpty())
    }
}
