package com.vatradar.app.data.repository

import com.vatradar.app.data.local.AirportDao
import com.vatradar.app.data.local.FirBoundaryStore
import com.vatradar.app.data.remote.VatsimApiService
import com.vatradar.app.domain.model.Aircraft
import com.vatradar.app.domain.model.Controller
import com.vatradar.app.domain.model.FacilityType
import kotlinx.coroutines.CancellationException

data class VatsimSnapshot(
    val aircraftList: List<Aircraft>,
    val controllerList: List<Controller>,
    val updatedAt: String
)

sealed class VatsimResult {
    data class Success(val snapshot: VatsimSnapshot) : VatsimResult()
    data class Error(val message: String, val throwable: Throwable? = null) : VatsimResult()
}

class VatsimRepository(
    private val apiService: VatsimApiService,
    private val airportDao: AirportDao,
    private val firBoundaryStore: FirBoundaryStore
) {
    suspend fun fetchSnapshot(): VatsimResult {
        return try {
            val response = apiService.getVatsimData()

            val aircraftList = response.pilots
                // 좌표가 (0,0)인 비정상 데이터 방어적 필터링
                .filter { it.latitude != 0.0 || it.longitude != 0.0 }
                .map { pilot ->
                    Aircraft(
                        cid = pilot.cid,
                        callsign = pilot.callsign,
                        pilotName = pilot.name,
                        latitude = pilot.latitude,
                        longitude = pilot.longitude,
                        altitude = pilot.altitude,
                        groundSpeed = pilot.groundspeed,
                        heading = pilot.heading.toFloat(),
                        aircraftType = pilot.flightPlan?.aircraftShort,
                        departure = pilot.flightPlan?.departure,
                        arrival = pilot.flightPlan?.arrival,
                        route = pilot.flightPlan?.route,
                        flightRules = pilot.flightPlan?.flightRules
                    )
                }

            // OBS(관찰자)만 제외합니다.
            // FSS는 유럽·오세아니아에서 실제 광역 관제로 쓰이므로 포함합니다.
            val activeControllers = response.controllers.filter {
                it.facility != FacilityType.OBS.code
            }

            // 관제사 좌표는 피드에 없으므로 콜사인 접두사를 공항 DB에서 조회해 채웁니다.
            //
            // 지역마다 콜사인 규칙이 달라 두 번 조회합니다.
            //   유럽/아시아: EDDF_TWR, RKSI_TWR → ICAO 그대로
            //   미주:        BOS_TWR, LGA_GND  → IATA (ICAO는 KBOS, KLGA)
            val prefixes = activeControllers.map { it.callsign.substringBefore('_') }.distinct()
            val byIcao = airportDao.findAllByIcao(prefixes).associateBy { it.icao }

            val unresolved = prefixes - byIcao.keys
            val byIata = if (unresolved.isEmpty()) emptyMap()
            else airportDao.findAllByIata(unresolved).associateBy { it.iata }

            val controllerList = activeControllers.map { c ->
                val facility = FacilityType.fromCode(c.facility)
                val prefix = c.callsign.substringBefore('_')
                val airport = byIcao[prefix] ?: byIata[prefix]

                // 광역 관제(CTR/FSS)만 FIR 폴리곤을 찾습니다.
                // 공항 관제는 폴리곤이 아니라 공항 좌표 마커가 맞습니다.
                val boundary = if (facility == FacilityType.CTR || facility == FacilityType.FSS) {
                    firBoundaryStore.boundariesFor(c.callsign)
                } else {
                    emptyList()
                }

                // 폴리곤이 있으면 그 무게중심을 라벨 위치로 씁니다.
                val center = boundary.takeIf { it.isNotEmpty() }
                    ?.let { firBoundaryStore.centroid(it) }

                Controller(
                    cid = c.cid,
                    name = c.name,
                    callsign = c.callsign,
                    frequency = c.frequency,
                    facility = facility,
                    textAtis = c.textAtis ?: emptyList(),
                    visualRangeNm = c.visualRange,
                    latitude = airport?.latitude ?: center?.latitude,
                    longitude = airport?.longitude ?: center?.longitude,
                    airportName = airport?.name,
                    boundary = boundary
                )
            }

            VatsimResult.Success(
                VatsimSnapshot(
                    aircraftList = aircraftList,
                    controllerList = controllerList,
                    updatedAt = response.general.updateTimestamp
                )
            )
        } catch (e: CancellationException) {
            // 코루틴 취소는 에러가 아니라 정상적인 흐름이므로 반드시 다시 던져야 합니다.
            // 삼키면 구조적 동시성이 깨지고, 주기 갱신 시 화면 이탈이 "오류"로 표시됩니다.
            throw e
        } catch (e: Exception) {
            VatsimResult.Error(
                message = "VATSIM 데이터를 불러오지 못했습니다: ${e.message}",
                throwable = e
            )
        }
    }

    /** F4: 관심 키워드에 해당하는 관제사가 현재 접속 중인지 확인합니다. */
    suspend fun onlineCallsignsMatching(keywords: Set<String>): List<String> {
        if (keywords.isEmpty()) return emptyList()
        val response = apiService.getVatsimData()
        return response.controllers
            .filter { it.facility != FacilityType.OBS.code }
            .map { it.callsign.uppercase() }
            .filter { callsign -> keywords.any { callsign.startsWith(it.uppercase()) } }
            .distinct()
    }
}
