package com.vatradar.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vatradar.app.data.prefs.UserSettings
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.ui.alerts.AlertsScreen
import com.vatradar.app.ui.events.EventsScreen
import com.vatradar.app.ui.map.MapScreen
import com.vatradar.app.ui.nav.Destination
import com.vatradar.app.ui.myflights.MyFlightsScreen
import com.vatradar.app.ui.route.ChallengeMapScreen
import com.vatradar.app.ui.route.RouteScreen
import com.vatradar.app.ui.settings.LicensesScreen
import com.vatradar.app.ui.settings.OssLicensesScreen
import com.vatradar.app.ui.settings.SettingsScreen
import com.vatradar.app.ui.theme.ThemeMode
import com.vatradar.app.ui.theme.VatFlightTheme

/**
 * AppCompatActivity를 쓰는 이유는 앱별 언어 설정 때문입니다.
 * AppCompatDelegate.setApplicationLocales()는 활성 AppCompat 델리게이트가 있어야
 * 시스템 LocaleManager에 도달합니다. 순수 ComponentActivity로는 호출이 무시됩니다.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = ServiceLocator.settingsRepository(this)

        setContent {
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = UserSettings())

            VatFlightTheme(themeMode = ThemeMode.fromTag(settings.themeMode)) {
                VatFlightRoot()
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VatFlightRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    val isSettings = currentRoute?.route == Destination.SETTINGS
    val isLicenses = currentRoute?.route == Destination.LICENSES
    val isOssLicenses = currentRoute?.route == Destination.OSS_LICENSES
    val isChallengeMap = currentRoute?.route == Destination.CHALLENGE_MAP
    val isSubPage = isSettings || isLicenses || isOssLicenses || isChallengeMap

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val labelRes = Destination.entries
                        .firstOrNull { d -> currentRoute?.hierarchy?.any { it.route == d.route } == true }
                        ?.labelRes
                    Text(
                        when {
                            isSettings -> stringResource(R.string.settings)
                            isLicenses -> stringResource(R.string.licenses)
                            isOssLicenses -> stringResource(R.string.open_source_licenses)
                            isChallengeMap -> stringResource(R.string.active_challenge)
                            labelRes != null -> stringResource(labelRes)
                            else -> stringResource(R.string.app_name)
                        }
                    )
                },
                navigationIcon = {
                    if (isSubPage) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                actions = {
                    if (!isSubPage) {
                        IconButton(onClick = { navController.navigate(Destination.SETTINGS) }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isSubPage) {
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
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = stringResource(destination.labelRes)
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
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
            composable(Destination.ROUTE.route) {
                RouteScreen(
                    onOpenChallengeMap = { origin, destination ->
                        navController.navigate(Destination.challengeMap(origin, destination))
                    }
                )
            }
            composable(Destination.EVENTS.route) { EventsScreen() }
            composable(Destination.ALERTS.route) { AlertsScreen() }
            composable(Destination.MY_FLIGHTS.route) { MyFlightsScreen() }
            composable(Destination.SETTINGS) {
                SettingsScreen(onOpenLicenses = { navController.navigate(Destination.LICENSES) })
            }
            composable(Destination.LICENSES) {
                LicensesScreen(
                    onOpenOssLicenses = { navController.navigate(Destination.OSS_LICENSES) }
                )
            }
            composable(Destination.OSS_LICENSES) { OssLicensesScreen() }
            composable(Destination.CHALLENGE_MAP) { entry ->
                ChallengeMapScreen(
                    origin = entry.arguments?.getString("origin").orEmpty(),
                    destination = entry.arguments?.getString("destination").orEmpty()
                )
            }
        }
    }
}
