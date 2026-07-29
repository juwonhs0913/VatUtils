package com.vatradar.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    MAP("map", "Map", Icons.Default.Map),
    ROUTE("route", "Route", Icons.Default.Route),
    EVENTS("events", "Events", Icons.Default.CalendarMonth);

    companion object {
        const val SETTINGS = "settings"
    }
}
