package com.vatradar.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.vatradar.app.R

enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    MAP("map", R.string.tab_map, Icons.Default.Map),
    ROUTE("route", R.string.tab_route, Icons.Default.Route),
    EVENTS("events", R.string.tab_events, Icons.Default.CalendarMonth),
    MY_FLIGHTS("myflights", R.string.tab_my_flights, Icons.Default.Flight);

    companion object {
        const val SETTINGS = "settings"
        const val ALERTS = "alerts"

        /** 뽑힌 경로를 지도에서 보는 화면. 출도착 ICAO를 경로 인자로 받습니다. */
        const val CHALLENGE_MAP = "challenge/{origin}/{destination}"

        fun challengeMap(origin: String, destination: String) = "challenge/$origin/$destination"
    }
}
