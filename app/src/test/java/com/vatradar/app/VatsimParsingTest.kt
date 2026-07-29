package com.vatradar.app

import com.vatradar.app.data.remote.dto.VatsimDataResponse
import com.vatradar.app.domain.model.FacilityType
import com.vatradar.app.util.toKstDisplay
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * README 체크리스트 2·3번을 기기 없이 검증합니다.
 * NetworkModule과 동일한 Json 설정을 사용해야 의미가 있습니다.
 */
class VatsimParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val sample = """
    {
      "general": {
        "version": 3,
        "reload": 1,
        "update": "20260729100000",
        "update_timestamp": "2026-07-29T10:00:00.0000000Z",
        "connected_clients": 1234,
        "unique_users": 1200
      },
      "pilots": [
        {
          "cid": 111, "name": "Pilot With FP", "callsign": "KAL123",
          "server": "AMSTERDAM", "pilot_rating": 1,
          "latitude": 37.46, "longitude": 126.44,
          "altitude": 35000, "groundspeed": 480,
          "transponder": "2000", "heading": 90,
          "flight_plan": {
            "flight_rules": "I", "aircraft_short": "A333",
            "departure": "RKSI", "arrival": "KLAX", "route": "DCT"
          },
          "logon_time": "2026-07-29T08:00:00Z",
          "last_updated": "2026-07-29T10:00:00Z"
        },
        {
          "cid": 222, "name": "No Flightplan", "callsign": "OBS001",
          "server": "AMSTERDAM", "pilot_rating": 0,
          "latitude": 35.0, "longitude": 129.0,
          "altitude": 0, "groundspeed": 0,
          "transponder": "0000", "heading": 0,
          "flight_plan": null,
          "logon_time": "2026-07-29T09:00:00Z",
          "last_updated": "2026-07-29T10:00:00Z"
        },
        {
          "cid": 333, "name": "Zero Coord", "callsign": "BAD999",
          "server": "AMSTERDAM", "pilot_rating": 0,
          "latitude": 0.0, "longitude": 0.0,
          "altitude": 0, "groundspeed": 0,
          "transponder": "0000", "heading": 0,
          "logon_time": "2026-07-29T09:30:00Z",
          "last_updated": "2026-07-29T10:00:00Z"
        }
      ],
      "controllers": [
        {
          "cid": 900, "name": "Obs Only", "callsign": "RKSI_OBS",
          "frequency": "199.998", "facility": 0, "rating": 1,
          "server": "AMSTERDAM", "visual_range": 20,
          "text_atis": null,
          "logon_time": "2026-07-29T09:00:00Z",
          "last_updated": "2026-07-29T10:00:00Z"
        },
        {
          "cid": 901, "name": "Incheon Tower", "callsign": "RKSI_TWR",
          "frequency": "118.200", "facility": 4, "rating": 3,
          "server": "AMSTERDAM", "visual_range": 50,
          "text_atis": ["INFO A", "RWY 33L"],
          "logon_time": "2026-07-29T09:00:00Z",
          "last_updated": "2026-07-29T10:00:00Z"
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `flight_plan이 없는 조종사도 예외 없이 파싱된다`() {
        val response = json.decodeFromString<VatsimDataResponse>(sample)
        val noFp = response.pilots.first { it.callsign == "OBS001" }
        assertNull(noFp.flightPlan)
    }

    @Test
    fun `flight_plan 키 자체가 누락돼도 파싱된다`() {
        val response = json.decodeFromString<VatsimDataResponse>(sample)
        val missing = response.pilots.first { it.callsign == "BAD999" }
        assertNull(missing.flightPlan)
    }

    @Test
    fun `좌표 0 0 데이터는 필터링 대상으로 식별된다`() {
        val response = json.decodeFromString<VatsimDataResponse>(sample)
        val valid = response.pilots.filter { it.latitude != 0.0 || it.longitude != 0.0 }
        assertEquals(2, valid.size)
    }

    @Test
    fun `알 수 없는 필드가 추가돼도 파싱이 깨지지 않는다`() {
        val withExtra = sample.replace(
            "\"version\": 3,",
            "\"version\": 3, \"brand_new_field\": \"surprise\","
        )
        val response = json.decodeFromString<VatsimDataResponse>(withExtra)
        assertEquals(3, response.general.version)
    }

    @Test
    fun `facility 코드가 FacilityType으로 매핑된다`() {
        assertEquals(FacilityType.TWR, FacilityType.fromCode(4))
        assertEquals(FacilityType.CTR, FacilityType.fromCode(6))
        // 미지의 코드는 OBS로 폴백
        assertEquals(FacilityType.OBS, FacilityType.fromCode(99))
    }

    @Test
    fun `UTC 타임스탬프가 KST로 변환된다`() {
        assertEquals(
            "2026-07-29 19:00:00 KST",
            "2026-07-29T10:00:00.0000000Z".toKstDisplay()
        )
    }

    @Test
    fun `파싱 불가능한 타임스탬프는 원문을 유지한다`() {
        assertEquals("not-a-date", "not-a-date".toKstDisplay())
    }
}
