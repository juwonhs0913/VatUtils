package com.vatradar.app.ui.myflights

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import android.view.MotionEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalView
import com.vatradar.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MyFlightsScreen(viewModel: MyFlightsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.cid.isBlank()) {
            Card {
                Text(
                    stringResource(R.string.cid_needed),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            return@Column
        }

        // ---------------- 통계 ----------------
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("%,.1f".format(state.totalHours), stringResource(R.string.stat_hours))
                    Stat("%,d".format(state.totalDistanceNm), stringResource(R.string.stat_distance))
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat("${state.countryCount}", stringResource(R.string.stat_countries))
                    Stat("${state.airportCount}", stringResource(R.string.stat_airports))
                    Stat("${state.flightCount}", stringResource(R.string.stat_flights))
                }
            }
        }

        // ---------------- 전 세계 정복 ----------------
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.countries_progress, state.countryCount, state.totalCountries),
                    style = MaterialTheme.typography.titleMedium
                )
                LinearProgressIndicator(
                    progress = {
                        if (state.totalCountries == 0) 0f
                        else state.countryCount.toFloat() / state.totalCountries
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.countries_goal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------------- 비행한 나라 지도 ----------------
        Card {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(LatLng(20.0, 10.0), 0.6f)
                }
                val mapTouchOwner = LocalView.current.parent
                GoogleMap(
                    // 세로 스크롤 안에 지도가 있으면 끌었을 때 페이지가 먼저 움직여
                    // 지도를 조작할 수 없습니다. 지도 위에서 시작한 터치는
                    // 지도가 가져가도록 부모의 가로채기를 막습니다.
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInteropFilter { event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN ->
                                    mapTouchOwner.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                    mapTouchOwner.requestDisallowInterceptTouchEvent(false)
                            }
                            false   // 이벤트는 지도에게 그대로 넘깁니다
                        },
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(mapType = MapType.NORMAL),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, scrollGesturesEnabled = true)
                ) {
                    state.visitedShapes.forEach { ring ->
                        Polygon(
                            points = ring,
                            fillColor = Color(0x883F6FB5),
                            strokeColor = Color(0xFF2C5590),
                            strokeWidth = 2f
                        )
                    }
                }
            }
        }

        // ---------------- 기록 시작 시점 안내 ----------------
        state.since?.let { since ->
            Text(
                stringResource(R.string.logbook_since, formatDate(since)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.padding(16.dp))
            }
        }

        // ---------------- 최근 비행 ----------------
        if (state.flights.isEmpty() && !state.loading) {
            Text(
                stringResource(R.string.no_flights_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(stringResource(R.string.recent_flights), style = MaterialTheme.typography.titleMedium)
            state.flights.take(30).forEach { flight ->
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${flight.departure} → ${flight.arrival}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            buildString {
                                append(flight.callsign)
                                flight.aircraft?.let { append(" · ").append(it) }
                                append(" · ")
                                append("%,.1fh".format(flight.hours))
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatDate(flight.startedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))
