package com.vatradar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vatradar.app.ui.events.EventsScreen
import com.vatradar.app.ui.map.MapScreen
import com.vatradar.app.ui.nav.Destination
import com.vatradar.app.ui.route.RouteScreen
import com.vatradar.app.ui.settings.SettingsScreen
import com.vatradar.app.ui.theme.VatRadarTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VatRadarTheme {
                VatRadarRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VatRadarRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val isSettings = currentRoute?.route == Destination.SETTINGS

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isSettings) "설정"
                        else Destination.entries
                            .firstOrNull { d -> currentRoute?.hierarchy?.any { it.route == d.route } == true }
                            ?.label ?: "VATRadar"
                    )
                },
                navigationIcon = {
                    if (isSettings) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                        }
                    }
                },
                actions = {
                    if (!isSettings) {
                        IconButton(onClick = { navController.navigate(Destination.SETTINGS) }) {
                            Icon(Icons.Default.Settings, contentDescription = "설정")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isSettings) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        val selected = currentRoute?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.MAP.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.MAP.route) { MapScreen() }
            composable(Destination.ROUTE.route) { RouteScreen() }
            composable(Destination.EVENTS.route) { EventsScreen() }
            composable(Destination.SETTINGS) { SettingsScreen() }
        }
    }
}
