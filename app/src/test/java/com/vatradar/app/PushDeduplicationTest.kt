package com.vatradar.app

import com.vatradar.app.notification.ControllerWatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 푸시 알림 중복 제거.
 *
 * 알림 경로가 둘(Cloudflare 푸시 + 15분 폴링)이라 서버가 죽어도 알림이 끊기지 않지만,
 * 그 대가로 같은 관제소를 두 번 알릴 위험이 생깁니다. 두 경로가 같은 기록을 공유해
 * 먼저 도착한 쪽만 알리도록 하는 게 이 로직입니다.
 */
class PushDeduplicationTest {

    private fun select(
        incoming: List<String>,
        watched: Set<String>,
        already: Set<String> = emptySet()
    ) = ControllerWatcher.selectNewCallsigns(incoming, watched, already)

    @Test
    fun `등록한 접두사에 걸리는 콜사인만 알린다`() {
        val result = select(
            incoming = listOf("RKSI_TWR", "EDDF_TWR"),
            watched = setOf("RKSI")
        )
        assertEquals(listOf("RKSI_TWR"), result)
    }

    @Test
    fun `접두사는 하위 관제석을 모두 포함한다`() {
        val result = select(
            incoming = listOf("RKSI_TWR", "RKSI_APP", "RKSI_GND"),
            watched = setOf("RKSI")
        )
        assertEquals(listOf("RKSI_APP", "RKSI_GND", "RKSI_TWR"), result)
    }

    @Test
    fun `이미 알린 콜사인은 다시 알리지 않는다`() {
        // 폴링이 먼저 알린 뒤 푸시가 도착한 상황
        val result = select(
            incoming = listOf("RKSI_TWR"),
            watched = setOf("RKSI"),
            already = setOf("RKSI_TWR")
        )
        assertTrue("중복 알림이 발생합니다", result.isEmpty())
    }

    @Test
    fun `일부만 이미 알린 경우 나머지만 알린다`() {
        val result = select(
            incoming = listOf("RKSI_TWR", "RKSI_APP"),
            watched = setOf("RKSI"),
            already = setOf("RKSI_TWR")
        )
        assertEquals(listOf("RKSI_APP"), result)
    }

    /**
     * 사용자가 관제소를 지웠는데 FCM 토픽 구독 해제가 실패할 수 있습니다
     * (네트워크 오류 등). 그 경우에도 알림이 오면 안 됩니다.
     */
    @Test
    fun `등록을 지운 관제소는 구독이 남아 있어도 알리지 않는다`() {
        val result = select(
            incoming = listOf("ZSPD_TWR"),
            watched = setOf("RKSI")
        )
        assertTrue("지운 관제소의 알림이 통과합니다", result.isEmpty())
    }

    @Test
    fun `등록된 관제소가 없으면 아무것도 알리지 않는다`() {
        assertTrue(select(listOf("RKSI_TWR"), emptySet()).isEmpty())
    }

    @Test
    fun `대소문자와 공백이 섞여도 정상 판정한다`() {
        val result = select(
            incoming = listOf(" rksi_twr ", "RKSI_APP"),
            watched = setOf(" rksi ")
        )
        assertEquals(listOf("RKSI_APP", "RKSI_TWR"), result)
    }

    @Test
    fun `같은 콜사인이 두 번 들어와도 한 번만 알린다`() {
        // 서버가 공항 토픽과 관제석 토픽 양쪽으로 보내면 중복 수신될 수 있습니다.
        val result = select(
            incoming = listOf("RKSI_TWR", "RKSI_TWR"),
            watched = setOf("RKSI")
        )
        assertEquals(listOf("RKSI_TWR"), result)
    }

    @Test
    fun `전체 콜사인으로 등록한 경우도 동작한다`() {
        val result = select(
            incoming = listOf("RKRR_CTR"),
            watched = setOf("RKRR_CTR")
        )
        assertEquals(listOf("RKRR_CTR"), result)
    }
}
