package com.vatradar.app.domain.model

/**
 * 경로 뽑기 범위.
 *
 * 거리 구간(단·중·장거리) 대신 지역으로 고릅니다. 둘 다 비우면 전 세계에서 뽑습니다.
 * 국가를 고르면 대륙은 무시됩니다 — 국가가 더 좁은 조건이라 함께 볼 이유가 없습니다.
 *
 * 여기서 말하는 "범위"는 **출발지**에 걸립니다. 도착지까지 같은 나라로 묶으면
 * 국내선만 나와서, 나라를 고르는 재미가 사라집니다.
 */
data class RouteFilter(
    /** ISO 대륙 코드 (AS/EU/NA/SA/AF/OC/AN). null이면 전 세계. */
    val continent: String? = null,
    /** ISO 2자리 국가 코드. null이면 대륙 전체. */
    val country: String? = null
) {
    // 범위 판정은 AirportRepository.scopeOf가 합니다. 여기서 airport.continent를 그대로
    // 보면 안 되기 때문입니다 — 나라는 대륙 하나에만 올리는데 공항에 붙은 대륙은
    // 그렇지 않아서(스페인의 카나리아 공항은 AF), 두 기준이 어긋납니다.

    val isWorldwide: Boolean get() = continent == null && country == null
}

/** 화면에 보여줄 대륙 목록. 남극은 정기편이 없어 뺍니다. */
enum class Continent(val code: String, val displayName: String) {
    ASIA("AS", "Asia"),
    EUROPE("EU", "Europe"),
    NORTH_AMERICA("NA", "North America"),
    SOUTH_AMERICA("SA", "South America"),
    AFRICA("AF", "Africa"),
    OCEANIA("OC", "Oceania");

    companion object {
        fun fromCode(code: String?): Continent? = entries.find { it.code == code }
    }
}
