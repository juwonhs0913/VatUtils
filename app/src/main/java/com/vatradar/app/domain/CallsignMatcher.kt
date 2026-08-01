package com.vatradar.app.domain

/**
 * 관제 콜사인 매칭.
 *
 * 문제: 사용자가 `RKRR_CTR`을 등록했는데 실제로는 `RKRR_A_CTR`(섹터 분할)로 접속합니다.
 * 앞부분만 비교하면 `RKRR_A_CTR`.startsWith(`RKRR_CTR`)가 false라 알림이 안 뜹니다.
 * 이런 중간 토큰은 흔합니다 — `LON_S_CTR`, `EGLL_N_APP`, `ZLA_37_CTR` 등.
 *
 * 그래서 접속한 콜사인마다 "같은 자리로 볼 수 있는 이름"을 몇 개 만들어 두고 비교합니다.
 * 서버(server-cloudflare/src/index.js의 aliasesFor)와 규칙이 같아야 합니다.
 */
object CallsignMatcher {

    /**
     * 콜사인 하나가 대표할 수 있는 이름들.
     *   RKRR_A_CTR → [RKRR_A_CTR, RKRR, RKRR_CTR]
     *   RKSI_TWR   → [RKSI_TWR, RKSI]
     */
    fun aliasesFor(callsign: String): Set<String> {
        val normalized = callsign.trim().uppercase()
        if (normalized.isEmpty()) return emptySet()

        val parts = normalized.split('_').filter { it.isNotEmpty() }
        val aliases = linkedSetOf(normalized)
        if (parts.isNotEmpty()) aliases += parts.first()
        // 가운데 토큰을 걷어낸 형태 — 섹터가 나뉘어도 같은 자리로 보이게 합니다.
        if (parts.size >= 3) aliases += parts.first() + "_" + parts.last()
        return aliases
    }

    /** 접속한 [callsign]이 사용자가 등록한 [watched]에 해당하는지. */
    fun matches(callsign: String, watched: String): Boolean {
        val target = watched.trim().uppercase()
        if (target.isEmpty()) return false
        val aliases = aliasesFor(callsign)
        // 별칭이 정확히 같거나, 등록값이 더 넓은 범위(RKSI → RKSI_TWR)인 경우
        return target in aliases || aliases.any { it.startsWith(target + "_") }
    }

    fun matchesAny(callsign: String, watched: Collection<String>): Boolean =
        watched.any { matches(callsign, it) }
}
