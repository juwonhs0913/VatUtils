package com.vatradar.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vatradar.app.auth.VatsimConnect
import com.vatradar.app.data.prefs.UserSettings
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.notification.FcmTopics
import com.vatradar.app.ui.alerts.AlertsScreen
import com.vatradar.app.ui.events.EventsScreen
import com.vatradar.app.ui.map.MapScreen
import com.vatradar.app.ui.nav.Destination
import com.vatradar.app.ui.route.RouteScreen
import com.vatradar.app.ui.settings.SettingsScreen
import com.vatradar.app.ui.theme.ThemeMode
import com.vatradar.app.ui.theme.VatRadarTheme
import kotlinx.coroutines.launch

/**
 * AppCompatActivity를 쓰는 이유는 앱별 언어 설정 때문입니다.
 * AppCompatDelegate.setApplicationLocales()는 활성 AppCompat 델리게이트가 있어야
 * 시스템 LocaleManager에 도달합니다. 순수 ComponentActivity로는 호출이 무시됩니다.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = ServiceLocator.settingsRepository(this)
        handleAuthCallback(intent)

        setContent {
            val settings by settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = UserSettings())

            VatRadarTheme(themeMode = ThemeMode.fromTag(settings.themeMode)) {
                VatRadarRoot()
            }
        }
    }

    /** 로그인을 마친 브라우저가 vatradar://auth 로 돌아옵니다 (singleTask). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(intent: Intent?) {
        val link = VatsimConnect.parseCallback(intent?.data) ?: return

        // 딥링크는 한 번만 처리해야 합니다. 지우지 않으면 화면 회전 등으로
        // 액티비티가 다시 만들어질 때 같은 토큰을 또 저장하게 됩니다.
        intent?.data = null

        lifecycleScope.launch {
            val repository = ServiceLocator.settingsRepository(this@MainActivity)
            val previous = repository.current().vatsimCid
            if (previous.isNotBlank() && previous != link.cid) {
                FcmTopics.unsubscribeCid(previous)
            }
            repository.setVatsimLink(link.cid, link.token)
            FcmTopics.subscribeCid(link.cid)
            Log.d("VATRadar", "VATSIM 연결됨: CID ${link.cid}")
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
    val isAlerts = currentRoute?.route == Destination.ALERTS
    val isSubPage = isSettings || isAlerts

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
                            isAlerts -> stringResource(R.string.alerts)
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
                        IconButton(onClick = { navController.navigate(Destination.ALERTS) }) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = stringResource(R.string.alerts)
                            )
                        }
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
            composable(Destination.ROUTE.route) { RouteScreen() }
            composable(Destination.EVENTS.route) { EventsScreen() }
            composable(Destination.SETTINGS) { SettingsScreen() }
            composable(Destination.ALERTS) { AlertsScreen() }
        }
    }
}
