package com.vatradar.app

import com.vatradar.app.data.repository.friendlyMessage
import com.vatradar.app.data.repository.isNumericPilotId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SimBrief 식별자 판별.
 *
 * SimBrief는 별칭과 숫자 Pilot ID의 질의 파라미터가 다릅니다(username / userid).
 * 숫자 ID를 username으로 보내면 그 숫자를 별칭으로 검색해 "Unknown UserID"가 납니다.
 */
class SimBriefIdentifierTest {

    @Test
    fun `숫자로만 된 값은 Pilot ID로 본다`() {
        assertTrue(isNumericPilotId("123456"))
        assertTrue(isNumericPilotId("1"))
        assertTrue(isNumericPilotId("000123"))
    }

    @Test
    fun `문자가 섞이면 별칭으로 본다`() {
        assertFalse(isNumericPilotId("juwon"))
        assertFalse(isNumericPilotId("pilot123"))
        assertFalse(isNumericPilotId("123abc"))
        assertFalse(isNumericPilotId("KAL_123"))
    }

    @Test
    fun `빈 값은 Pilot ID로 보지 않는다`() {
        assertFalse(isNumericPilotId(""))
    }

    // --- 오류 메시지 ---

    @Test
    fun `Unknown UserID를 확인 방법과 함께 안내한다`() {
        val message = friendlyMessage("Error: Unknown UserID")
        assertTrue(message!!.contains("Alias"))
        assertTrue(message.contains("Pilot ID"))
    }

    @Test
    fun `비행계획 없음을 다음 행동과 함께 안내한다`() {
        val message = friendlyMessage("Error: No flight plan on file for the specified user")
        assertTrue(message!!.contains("Generate"))
    }

    @Test
    fun `알 수 없는 오류는 원문을 그대로 전달한다`() {
        assertEquals("Error: Something else", friendlyMessage("Error: Something else"))
    }

    @Test
    fun `null은 그대로 null이다`() {
        assertNull(friendlyMessage(null))
    }
}
