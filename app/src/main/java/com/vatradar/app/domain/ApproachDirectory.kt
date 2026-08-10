package com.vatradar.app.domain

import com.vatradar.app.domain.model.Airport

/**
 * 관찰된 관제석 이름 중에서 **이 나라의 접근관제석**을 골라냅니다.
 *
 * 서버가 모아 둔 목록은 콜사인뿐입니다 — 어느 나라 것인지는 적혀 있지 않습니다.
 * 그래서 콜사인 앞머리를 공항 데이터로 되짚어야 하는데, 앞머리에 쓰는 코드가
 * 나라마다 다릅니다.
 *
 *   RKSS_APP      ICAO 그대로            → 인천·김포를 함께 보는 서울 어프로치
 *   ATL_APP       미주는 IATA를 씁니다    → KATL(애틀랜타)
 *   EKDK_APP      공항이 아니라 FIR 코드  → EK 접두사 = 덴마크
 *   BN_APP        VATPAC 고유 약어        → 브리즈번. 데이터로는 알 수 없습니다
 *
 * 예전에는 첫 번째 규칙만 있었고, 그것도 국제공항급만 봤습니다. 그 결과 미국은
 * 접근관제석이 1,200개 중 하나도 뜨지 않았고(전부 IATA 표기라서) 호주도 마찬가지였습니다.
 * 네 규칙을 다 적용하면 93%가 어느 나라 것인지 정해집니다.
 *
 * 정하지 못한 나머지는 **버립니다**. 목록에 넣어 봐야 어느 나라 칩에서 나와야 하는지
 * 모르고, 아무 데나 넣으면 인천 어프로치를 지어내던 것과 같은 잘못이 됩니다.
 * 직접 입력으로는 언제든 등록할 수 있습니다.
 */
object ApproachDirectory {

    /** 목록에 올릴 접근관제석 하나. [servedBy]는 그 자리가 실제로 보는 공항 이름입니다. */
    data class Candidate(val callsign: String, val servedBy: String)

    private val SUFFIXES = setOf("APP", "DEP")

    /**
     * @param observed     서버가 모아 둔, 실제로 접속한 적이 있는 콜사인 전체
     * @param country      고른 나라의 ISO2 코드
     * @param airports     그 나라의 공항 전부 (국제공항급이 아닌 곳까지)
     * @param icaoPrefixes ICAO 접두사 → 국가 코드
     */
    fun candidatesFor(
        observed: Set<String>,
        country: String,
        airports: List<Airport>,
        icaoPrefixes: Map<String, String>
    ): List<Candidate> {
        if (observed.isEmpty()) return emptyList()

        val byIcao = airports.associateBy { it.icao }
        // IATA가 비어 있는 공항이 많아 그대로 associateBy 하면 빈 문자열 하나로 뭉갭니다.
        val byIata = airports.filter { it.iata.isNotEmpty() }.associateBy { it.iata }

        return observed.asSequence()
            .filter { it.substringAfterLast('_') in SUFFIXES }
            .mapNotNull { callsign ->
                // SY-D_APP처럼 하이픈으로 섹터를 나눈 표기가 있어 거기서도 끊습니다.
                val root = callsign.substringBefore('_').substringBefore('-')
                val servedBy = servedBy(root, country, byIcao, byIata, icaoPrefixes)
                servedBy?.let { Candidate(callsign, it) }
            }
            .sortedBy { it.callsign }
            .toList()
    }

    /** 이 앞머리가 [country] 것이면 표시할 이름을, 아니면 null을 돌려줍니다. */
    private fun servedBy(
        root: String,
        country: String,
        byIcao: Map<String, Airport>,
        byIata: Map<String, Airport>,
        icaoPrefixes: Map<String, String>
    ): String? {
        byIcao[root]?.let { return it.name }
        byIata[root]?.let { return it.name }

        // 공항이 아닌 4글자는 FIR 코드일 수 있습니다(EKDK, LOVV). 접두사로 나라를 봅니다.
        // 2글자를 먼저 보고, 그 접두사를 아예 모를 때만 1글자로 물러섭니다.
        if (root.length == 4 && root.all { it.isLetter() }) {
            val prefix = icaoPrefixes[root.take(2)] ?: icaoPrefixes[root.take(1)]
            if (prefix == country) return root
        }

        return NON_ICAO_ROOTS[root]?.takeIf { it.country == country }?.name
    }

    private data class KnownRoot(val country: String, val name: String)

    /**
     * 공항 코드가 아닌 앞머리.
     *
     * 데이터로는 풀 수 없어 하나씩 확인해 적었습니다. **확신이 서는 것만** 넣었습니다 —
     * 호주 VATPAC의 두 글자 약어와 미국의 TRACON 식별자가 대부분입니다.
     * 이 표가 없으면 호주는 접근관제석이 하나도 뜨지 않습니다(YPDN, YPLM 둘 제외).
     *
     * CH·SG·PE·WS·KC·BA·AM처럼 뜻이 갈리는 약어는 일부러 뺐습니다. 지어내느니 빠지는 편이 낫습니다.
     */
    private val NON_ICAO_ROOTS: Map<String, KnownRoot> = mapOf(
        // 호주 — VATPAC
        "AD" to KnownRoot("AU", "Adelaide"),
        "AS" to KnownRoot("AU", "Alice Springs"),
        "AV" to KnownRoot("AU", "Avalon"),
        "BN" to KnownRoot("AU", "Brisbane"),
        "CB" to KnownRoot("AU", "Canberra"),
        "CG" to KnownRoot("AU", "Gold Coast"),
        "CS" to KnownRoot("AU", "Cairns"),
        "DN" to KnownRoot("AU", "Darwin"),
        "ES" to KnownRoot("AU", "Essendon"),
        "HB" to KnownRoot("AU", "Hobart"),
        "LT" to KnownRoot("AU", "Launceston"),
        "MK" to KnownRoot("AU", "Mackay"),
        "ML" to KnownRoot("AU", "Melbourne"),
        "PH" to KnownRoot("AU", "Perth"),
        "SY" to KnownRoot("AU", "Sydney"),
        "TL" to KnownRoot("AU", "Townsville"),
        "TN" to KnownRoot("AU", "Tamworth"),
        "WLM" to KnownRoot("AU", "Williamtown"),

        // 미국 — TRACON. 공항 하나가 아니라 여러 공항을 묶어 보는 자리입니다.
        "A90" to KnownRoot("US", "Boston TRACON"),
        "D10" to KnownRoot("US", "Dallas-Fort Worth TRACON"),
        "F11" to KnownRoot("US", "Central Florida TRACON"),
        "I90" to KnownRoot("US", "Houston TRACON"),
        "N90" to KnownRoot("US", "New York TRACON"),
        "NCT" to KnownRoot("US", "Northern California TRACON"),
        "NY" to KnownRoot("US", "New York TRACON"),
        "PCT" to KnownRoot("US", "Potomac TRACON"),
        "SCT" to KnownRoot("US", "Southern California TRACON"),
        "T75" to KnownRoot("US", "St. Louis TRACON"),
        "ZDC" to KnownRoot("US", "Washington Center"),

        // 그 밖
        "THAMES" to KnownRoot("GB", "Thames Radar (London)"),
        "TYO" to KnownRoot("JP", "Tokyo")
    )
}
