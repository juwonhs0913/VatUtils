package com.vatradar.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
    EVENTS("events", R.string.tab_events, Icons.Default.CalendarMonth);

    companion object {
        const val SETTINGS = "settings"
    }
}
