package com.vatradar.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.vatradar.app.R
import com.vatradar.app.domain.model.Aircraft
import com.vatradar.app.domain.model.Controller
import com.vatradar.app.domain.model.FacilityType

/** 클러스터링에 넣기 위한 래퍼. equals/hashCode가 필요해 data class로 둡니다. */
data class AircraftClusterItem(val aircraft: Aircraft) : ClusterItem {
    override fun getPosition() = LatLng(aircraft.latitude, aircraft.longitude)
    override fun getTitle() = aircraft.callsign
    override fun getSnippet() = "${aircraft.departure ?: "?"} → ${aircraft.arrival ?: "?"}"
    override fun getZIndex() = 0f
}

/** 한 공항에 동시에 열린 TWR/GND/DEL 묶음. */
private data class BadgeGroup(
    val position: LatLng,
    val controllers: List<Controller>
) {
    val facilities: List<FacilityType> get() = controllers.map { it.facility }
}

@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clusterManagerRef = remember { mutableStateOf<ClusterManager<AircraftClusterItem>?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        // 초기 위치는 인천 부근 (한국 사용자 기준)
        position = CameraPosition.fromLatLngZoom(LatLng(36.5, 127.8), 4f)
    }

    val aircraftItems = remember(state.snapshot, state.showAircraft) {
        if (!state.showAircraft) emptyList()
        else state.snapshot?.aircraftList.orEmpty().map { AircraftClusterItem(it) }
    }

    val allControllers = remember(state.snapshot, state.showControllers) {
        if (!state.showControllers) emptyList()
        else state.snapshot?.controllerList.orEmpty()
    }

    // 광역 관제(CTR/FSS): VATSpy FIR 폴리곤
    val boundaryControllers = remember(allControllers) {
        allControllers.filter { it.hasBoundary }
    }

    // 접근 관제(APP): FIR 경계가 없으므로 가시 범위 반경의 원으로 담당 공역을 나타냅니다.
    val approachControllers = remember(allControllers) {
        allControllers.filter {
            it.facility == FacilityType.APP && it.latitude != null && it.longitude != null
        }
    }

    // 공항 관제(TWR/GND/DEL): 공항 옆 문자 배지. 공항 단위로 한 줄에 모읍니다.
    val badgeGroups = remember(allControllers) {
        allControllers
            .filter {
                AirportBadgeIcons.letterFor(it.facility) != null &&
                    it.latitude != null && it.longitude != null
            }
            .groupBy { it.prefix }
            .mapNotNull { (_, group) ->
                val first = group.first()
                BadgeGroup(
                    position = LatLng(first.latitude!!, first.longitude!!),
                    controllers = group.sortedBy { AirportBadgeIcons.sortKey(it.facility) }
                )
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.NORMAL),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
            onMapClick = { viewModel.dismissSheet() }
        ) {
            // 1) 광역 관제 구역 — FIR 폴리곤
            boundaryControllers.forEach { controller ->
                val color = facilityColor(controller.facility)
                controller.boundary.forEach { ring ->
                    Polygon(
                        points = ring,
                        strokeColor = color,
                        strokeWidth = 3f,
                        fillColor = color.copy(alpha = 0.12f),
                        clickable = true,
                        onClick = { viewModel.selectControllers(listOf(controller)) }
                    )
                }
            }

            // 2) 접근 관제 구역 — 가시 범위 반경 원
            approachControllers.forEach { controller ->
                val color = facilityColor(FacilityType.APP)
                Circle(
                    center = LatLng(controller.latitude!!, controller.longitude!!),
                    radius = controller.approachRadiusMeters(),
                    strokeColor = color,
                    strokeWidth = 3f,
                    fillColor = color.copy(alpha = 0.12f),
                    clickable = true,
                    onClick = { viewModel.selectControllers(listOf(controller)) }
                )
            }

            // 3) 공항 관제석 — T / G / D 배지
            badgeGroups.forEach { group ->
                val icon = AirportBadgeIcons.forFacilities(group.facilities)
                if (icon != null) {
                    val markerState = rememberMarkerState(
                        key = group.controllers.first().prefix,
                        position = group.position
                    )
                    Marker(
                        state = markerState,
                        icon = icon,
                        // 배지가 공항 점 오른쪽에 붙도록 왼쪽 중앙을 기준점으로 둡니다.
                        anchor = Offset(0f, 0.5f),
                        title = group.controllers.first().prefix,
                        onClick = {
                            viewModel.selectControllers(group.controllers)
                            true
                        }
                    )
                }
            }

            // 4) 항공기 — 축소 시 그룹화 (PRD 성능 요구사항).
            // ClusterManager를 직접 다뤄 네이티브 마커 회전을 씁니다 — MapEffect 안은
            // Compose 밖이라 항공기 수천 대에서도 컴포지션 비용이 들지 않습니다.
            MapEffect(aircraftItems) { map ->
                val manager = clusterManagerRef.value ?: ClusterManager<AircraftClusterItem>(
                    context, map
                ).also { created ->
                    created.renderer = AircraftRenderer(context, map, created)
                    created.setOnClusterItemClickListener { item ->
                        viewModel.selectAircraft(item.aircraft)
                        true
                    }
                    map.setOnCameraIdleListener(created)
                    map.setOnMarkerClickListener(created)
                    clusterManagerRef.value = created
                }

                manager.clearItems()
                manager.addItems(aircraftItems)
                manager.cluster()
            }
        }

        MapOverlay(
            state = state,
            airspaceCount = boundaryControllers.size + approachControllers.size,
            onToggleAircraft = viewModel::toggleAircraft,
            onToggleControllers = viewModel::toggleControllers,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        val aircraft = state.selectedAircraft
        val controllers = state.selectedControllers

        if (aircraft != null || controllers.isNotEmpty()) {
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissSheet,
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (aircraft != null) {
                        AircraftDetails(aircraft, onAirportClick = viewModel::loadWeather)
                    }
                    controllers.forEachIndexed { index, controller ->
                        if (index > 0) HorizontalDivider()
                        ControllerDetails(controller)
                    }
                    WeatherSectionHost(state)
                }
            }
        }
    }
}

@Composable
private fun WeatherSectionHost(state: MapUiState) {
    com.vatradar.app.ui.weather.WeatherSection(
        report = state.weather,
        loading = state.weatherLoading
    )
}

/**
 * APP 담당 공역 반경.
 * visual_range는 관제사가 설정하는 값이라 0이거나 비현실적으로 큰 경우가 있어 범위를 제한합니다.
 */
private fun Controller.approachRadiusMeters(): Double {
    val nm = visualRangeNm.coerceIn(20, 150)
    return nm * 1852.0
}

@Composable
private fun MapOverlay(
    state: MapUiState,
    airspaceCount: Int,
    onToggleAircraft: () -> Unit,
    onToggleControllers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onToggleAircraft,
                    label = {
                        Text(
                            stringResource(
                                R.string.aircraft_count,
                                state.snapshot?.aircraftList?.size ?: 0
                            )
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Flight, null, Modifier.size(16.dp)) },
                    colors = if (state.showAircraft) AssistChipDefaults.assistChipColors()
                    else AssistChipDefaults.assistChipColors(labelColor = Color.Gray)
                )
                AssistChip(
                    onClick = onToggleControllers,
                    label = {
                        Text(
                            stringResource(
                                R.string.controller_count,
                                state.snapshot?.controllerList?.size ?: 0
                            )
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Headset, null, Modifier.size(16.dp)) },
                    colors = if (state.showControllers) AssistChipDefaults.assistChipColors()
                    else AssistChipDefaults.assistChipColors(labelColor = Color.Gray)
                )
                if (state.isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }

            if (state.showControllers) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 구역으로 그려지는 시설
                    listOf(FacilityType.CTR, FacilityType.FSS, FacilityType.APP).forEach { facility ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            LegendDot(facilityColor(facility))
                            Text(facility.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    // 배지로 그려지는 시설
                    listOf(
                        FacilityType.TWR to "T",
                        FacilityType.GND to "G",
                        FacilityType.DEL to "D"
                    ).forEach { (facility, letter) ->
                        LegendBadge(letter, facilityColor(facility))
                    }
                }

                if (airspaceCount > 0) {
                    Text(
                        stringResource(R.string.airspace_shown, airspaceCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AircraftDetails(aircraft: Aircraft, onAirportClick: (String) -> Unit) {
    Text(aircraft.callsign, style = MaterialTheme.typography.headlineSmall)
    Text(
        "${aircraft.pilotName} · ${aircraft.aircraftType ?: stringResource(R.string.unknown_type)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        DetailStat(stringResource(R.string.altitude), "%,d ft".format(aircraft.altitude))
        DetailStat(stringResource(R.string.ground_speed), "${aircraft.groundSpeed} kt")
        DetailStat(stringResource(R.string.heading), "${aircraft.heading.toInt()}°")
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        aircraft.departure?.let {
            AssistChip(
                onClick = { onAirportClick(it) },
                label = { Text(stringResource(R.string.departure_chip, it)) }
            )
        }
        aircraft.arrival?.let {
            AssistChip(
                onClick = { onAirportClick(it) },
                label = { Text(stringResource(R.string.arrival_chip, it)) }
            )
        }
    }

    if (aircraft.departure != null || aircraft.arrival != null) {
        Text(
            stringResource(R.string.tap_airport_for_weather),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    aircraft.route?.takeIf { it.isNotBlank() }?.let {
        Text(stringResource(R.string.route_label), style = MaterialTheme.typography.labelMedium)
        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ControllerDetails(controller: Controller) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val letter = AirportBadgeIcons.letterFor(controller.facility)
        if (letter != null) {
            LegendBadge(letter, facilityColor(controller.facility))
        } else {
            LegendDot(facilityColor(controller.facility))
        }
        Text(controller.callsign, style = MaterialTheme.typography.headlineSmall)
    }
    Text(
        "${controller.name} · ${controller.facility.label}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        DetailStat(stringResource(R.string.frequency), controller.frequency)
        DetailStat(stringResource(R.string.visual_range), "${controller.visualRangeNm} nm")
    }
    controller.airportName?.let {
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
    if (controller.hasBoundary || controller.facility == FacilityType.APP) {
        Text(
            stringResource(R.string.airspace_drawn),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
    if (controller.textAtis.isNotEmpty()) {
        Text(stringResource(R.string.atis), style = MaterialTheme.typography.labelMedium)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                controller.textAtis.joinToString("\n"),
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** 관제 시설 종류별 색 (폴리곤·원·배지·범례에서 공용) */
fun facilityColor(facility: FacilityType): Color = when (facility) {
    FacilityType.CTR -> Color(0xFFD32F2F)
    FacilityType.APP -> Color(0xFFF57C00)
    FacilityType.TWR -> Color(0xFF1976D2)
    FacilityType.GND -> Color(0xFF388E3C)
    FacilityType.DEL -> Color(0xFF7B1FA2)
    FacilityType.FSS -> Color(0xFF00838F)
    FacilityType.OBS -> Color(0xFF9E9E9E)
}

@Composable
fun LegendDot(color: Color) {
    Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
}

/** 지도 배지와 같은 모양의 작은 사각 범례. */
@Composable
fun LegendBadge(letter: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(3.dp)) {
        Text(
            letter,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}
