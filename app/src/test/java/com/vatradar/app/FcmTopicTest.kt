package com.vatradar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FCM 토픽 이름 규칙.
 *
 * 앱(FcmTopics)과 Cloudflare Worker(server-cloudflare/src/index.js)가 **같은 규칙**으로
 * 토픽 이름을 만들어야 합니다. 한쪽만 바뀌면 구독과 발송이 어긋나 알림이 조용히 사라집니다.
 * 여기서는 그 규칙을 문서화하고 고정합니다.
 */
class FcmTopicTest {

    /** FcmTopics.normalize / Worker의 normalizeTopic 과 같은 구현. */
    private fun normalize(callsign: String): String =
        callsign.trim().uppercase().replace(Regex("[^A-Z0-9_.~%-]"), "_")

    private fun topic(callsign: String) = "cs_" + normalize(callsign)

    @Test
    fun `일반 콜사인은 그대로 대문자로 쓰인다`() {
        assertEquals("cs_RKSI", topic("RKSI"))
        assertEquals("cs_RKSI_TWR", topic("RKSI_TWR"))
        assertEquals("cs_RKRR_CTR", topic("RKRR_CTR"))
    }

    @Test
    fun `소문자와 공백을 정리한다`() {
        assertEquals("cs_RKSI", topic("  rksi  "))
        assertEquals("cs_EDDF_TWR", topic("eddf_twr"))
    }

    @Test
    fun `FCM이 허용하지 않는 문자는 밑줄로 바꾼다`() {
        // 토픽 이름은 [a-zA-Z0-9-_.~%] 만 허용됩니다.
        assertEquals("cs_RKSI_1", topic("RKSI/1"))
        assertEquals("cs_LFPG_N", topic("LFPG N"))
        // 두 글자 → 밑줄 두 개
        assertEquals("cs___", topic("한글"))
    }

    @Test
    fun `허용 문자는 보존한다`() {
        assertEquals("cs_KZJX-C", topic("KZJX-C"))
        assertEquals("cs_A.B", topic("A.B"))
    }

    /**
     * 서버는 콜사인 하나를 두 토픽으로 보냅니다.
     * 공항 전체를 구독한 사람과 특정 관제석만 구독한 사람이 모두 받아야 하기 때문입니다.
     */
    @Test
    fun `콜사인 하나가 공항 토픽과 관제석 토픽 모두에 대응한다`() {
        val callsign = "RKSI_TWR"
        val expected = setOf(topic(callsign), topic(callsign.substringBefore('_')))
        assertEquals(setOf("cs_RKSI_TWR", "cs_RKSI"), expected)
    }

    @Test
    fun `접두사만 등록해도 하위 관제석이 걸린다`() {
        // 사용자가 RKSI를 등록하면 cs_RKSI를 구독하고,
        // 서버는 RKSI_TWR·RKSI_APP 무엇이 뜨든 cs_RKSI로도 보냅니다.
        listOf("RKSI_TWR", "RKSI_APP", "RKSI_GND", "RKSI_DEL").forEach {
            assertTrue(topic(it.substringBefore('_')) == "cs_RKSI")
        }
    }
}
