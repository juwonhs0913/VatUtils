package com.vatradar.app.domain

/**
 * 등록해 둔 관제소를 화면에 놓을 수 있는 모양으로 묶습니다.
 *
 * 저장된 것은 문자열 집합 하나뿐입니다 — `RKRR_CTR`, `RKSI`, `RKSS_APP`이 한 줄에
 * 섞여 있습니다. 센터와 공항은 성격이 아주 달라서(센터는 하나가 나라 절반을 덮고,
 * 공항은 자리가 여럿으로 쪼개집니다) 한 덩어리로 늘어놓으면 무엇을 등록해 뒀는지
 * 읽어내기가 어렵습니다. 그래서 여기서 갈라 둡니다.
 *
 * 공항은 한 걸음 더 들어갑니다. `RKSI` 하나만 저장돼 있으면 그 공항의 **모든** 자리를
 * 뜻합니다([Airport.all]). 사용자가 자리 하나를 끄는 순간 그 한 줄을 개별 항목
 * (`RKSI_DEL`, `RKSI_GND`)으로 풀어 씁니다 — 저장 형식은 그대로 두고 뜻만 좁힙니다.
 */
object WatchedStations {

    /** 공항마다 늘 보여 주는 세 자리. 관제탑 아래 순서대로. */
    val AIRPORT_POSITIONS = listOf("DEL", "GND", "TWR")

    private val CENTER_SUFFIXES = setOf("CTR", "FSS")

    data class Airport(
        val icao: String,
        /** 맨 ICAO로 등록돼 있어 모든 자리를 뜻하는 상태. */
        val all: Boolean,
        /** 개별로 등록된 자리들. [all]이면 비어 있습니다. */
        val positions: Set<String>,
        /** 실제로 저장돼 있는 항목들. 이 공항을 통째로 지울 때 씁니다. */
        val entries: Set<String>
    ) {
        /**
         * 화면에 그릴 상자들. DEL·GND·TWR은 늘 있고, 그 밖에 등록된 자리
         * (`RKSS_APP` 같은)가 있으면 뒤에 덧붙입니다. 등록해 둔 것이 화면에서
         * 사라지면 안 되니까요.
         */
        val boxes: List<String>
            get() = AIRPORT_POSITIONS + (positions - AIRPORT_POSITIONS.toSet()).sorted()

        fun isOn(position: String): Boolean = all || position in positions
    }

    data class Groups(
        val centers: List<String>,
        val airports: List<Airport>,
        /** 어느 쪽도 아닌 것 — 직접 입력한 `LON` 같은 접두사, `SCT_APP` 같은 TRACON. */
        val others: List<String>
    )

    /** 저장할 항목의 변화. 등록/해제와 FCM 구독을 함께 처리하려고 더하기·빼기를 나눠 줍니다. */
    data class Change(val add: Set<String> = emptySet(), val remove: Set<String> = emptySet())

    fun group(watched: Set<String>): Groups {
        val centers = mutableListOf<String>()
        val others = mutableListOf<String>()
        val airports = LinkedHashMap<String, Airport>()

        watched.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.sorted().forEach { entry ->
            val tokens = entry.split('_').filter { it.isNotEmpty() }
            val root = tokens.firstOrNull() ?: return@forEach
            val suffix = tokens.takeIf { it.size >= 2 }?.last()

            when {
                suffix != null && suffix in CENTER_SUFFIXES -> centers += entry

                root.length == 4 && root.all { it.isLetter() } -> {
                    val existing = airports[root]
                        ?: Airport(root, all = false, positions = emptySet(), entries = emptySet())
                    airports[root] = existing.copy(
                        all = existing.all || suffix == null,
                        positions = if (suffix == null) existing.positions
                        else existing.positions + suffix,
                        entries = existing.entries + entry
                    )
                }

                else -> others += entry
            }
        }

        return Groups(
            centers = centers,
            airports = airports.values.sortedBy { it.icao },
            others = others
        )
    }

    /**
     * 공항 [icao]의 자리 하나를 켜거나 끕니다.
     *
     * 맨 ICAO 한 줄로 "전부"였던 상태에서 하나를 끄면, 남은 자리들을 개별 항목으로
     * 적고 맨 ICAO는 지웁니다. 이때 뜻이 살짝 좁아집니다 — 맨 ICAO는 접근관제나 ATIS까지
     * 걸렸지만 개별 항목은 고른 것만 걸립니다. 화면에서 그 점을 알려 줍니다.
     */
    fun togglePosition(
        watched: Set<String>,
        icao: String,
        position: String,
        on: Boolean
    ): Change {
        val airport = group(watched).airports.firstOrNull { it.icao == icao } ?: return Change()
        if (airport.isOn(position) == on) return Change()

        // 끌 때는 그 자리를 가리키는 항목을 모두 지웁니다. 같은 자리가 여러 모양으로
        // 저장돼 있을 수 있습니다 — EGLL_APP과 EGLL_N_APP은 둘 다 히드로 접근관제입니다.
        val samePosition = airport.entries.filter { positionOf(it) == position }.toSet()

        if (airport.all) {
            // 여기 오는 경우는 끄는 쪽뿐입니다 — 전부 켜진 상태에서 켤 것은 없으니까요.
            val keep = (airport.boxes - position).filterNot { it in airport.positions }
            return Change(
                add = keep.map { "${icao}_$it" }.toSet(),
                remove = samePosition + icao
            )
        }

        return if (on) Change(add = setOf("${icao}_$position"))
        else Change(remove = samePosition)
    }

    private fun positionOf(entry: String): String? =
        entry.split('_').filter { it.isNotEmpty() }.takeIf { it.size >= 2 }?.last()
}
