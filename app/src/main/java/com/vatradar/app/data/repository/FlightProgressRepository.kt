package com.vatradar.app.data.repository

import android.util.Log
import com.vatradar.app.data.local.ChallengeEntity
import com.vatradar.app.data.remote.VatsimApiService
import com.vatradar.app.data.remote.VatsimMemberApiService
import com.vatradar.app.domain.FlightVerifier
import com.vatradar.app.domain.model.Aircraft
import kotlinx.coroutines.CancellationException

/**
 * 활성 챌린지를 실시간 피드와 대조해 진행 상황을 갱신합니다.
 *
 * 앱을 열 때와 15분 주기 작업에서 호출됩니다. 비행 중 한 번도 실행되지 않으면
 * 완주를 잡지 못할 수 있는데, 그래서 접속 종료 후 비행시간 증가로도 판정합니다.
 */
class FlightProgressRepository(
    private val vatsimApi: VatsimApiService,
    private val memberApi: VatsimMemberApiService,
    private val challenges: ChallengeRepository,
    private val airports: AirportRepository
) {

    /** @return 이번에 완주 처리된 챌린지 목록. */
    suspend fun sync(vatsimCid: String): List<ChallengeEntity> {
        if (vatsimCid.isBlank()) return emptyList()

        return try {
            challenges.expireStale()
            val active = challenges.activeChallenges()
            if (active.isEmpty()) return emptyList()

            val me = findMyAircraft(vatsimCid)
            val completed = mutableListOf<ChallengeEntity>()

            for (challenge in active) {
                val result = evaluate(challenge, me, vatsimCid)
                if (result != null) completed += result
            }
            completed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("VATRadar", "비행 진행 상황 확인 실패", e)
            emptyList()
        }
    }

    private suspend fun evaluate(
        challenge: ChallengeEntity,
        me: Aircraft?,
        vatsimCid: String
    ): ChallengeEntity? {
        if (me != null && FlightVerifier.matchesRoute(me, challenge)) {
            challenges.markSeenEnroute(challenge)

            val arrival = airports.find(challenge.destination)
            if (FlightVerifier.hasArrived(me, arrival)) {
                challenges.markCompleted(challenge)
                Log.d("VATRadar", "챌린지 완주(도착 관측): ${challenge.origin}→${challenge.destination}")
                return challenge
            }
            return null
        }

        // 피드에 없다 = 접속 종료. 비행 중인 걸 본 적이 있다면 시간 증가로 판정합니다.
        if (challenge.seenEnroute) {
            val hours = fetchPilotHours(vatsimCid)
            if (FlightVerifier.completedAfterDisconnect(challenge, hours)) {
                challenges.markCompleted(challenge)
                Log.d("VATRadar", "챌린지 완주(비행시간 증가): ${challenge.origin}→${challenge.destination}")
                return challenge
            }
        }
        return null
    }

    private suspend fun findMyAircraft(vatsimCid: String): Aircraft? {
        val cid = vatsimCid.trim().toIntOrNull() ?: return null
        return vatsimApi.getVatsimData().pilots
            .firstOrNull { it.cid == cid }
            ?.let { pilot ->
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
                    flightRules = pilot.flightPlan?.flightRules,
                    plannedDepartureHhmm = pilot.flightPlan?.deptime,
                    enrouteTimeHhmm = pilot.flightPlan?.enrouteTime
                )
            }
    }

    /** 챌린지 시작 시점의 기준값으로도 쓰이고, 완주 판정에도 쓰입니다. */
    suspend fun fetchPilotHours(vatsimCid: String): Double? = try {
        val response = memberApi.getStats(vatsimCid.trim())
        if (response.isSuccessful) response.body()?.pilot else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("VATRadar", "비행시간 조회 실패", e)
        null
    }
}
