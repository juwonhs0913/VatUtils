package com.vatradar.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.vatradar.app.R
import com.vatradar.app.domain.model.Aircraft
import com.vatradar.app.domain.model.Controller
import com.vatradar.app.domain.model.FacilityType
import com.vatradar.app.util.formatZuluHhmm

@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val markerController = remember { MapMarkerController() }

    val cameraPositionState = rememberCameraPositionState {
        // 초기 위치는 인천 부근 (한국 사용자 기준)
        position = CameraPosition.fromLatLngZoom(LatLng(36.5, 127.8), 4f)
    }

    val aircraftList = remember(state.snapshot, state.showAircraft) {
        if (!state.showAircraft) emptyList()
        else state.snapshot?.aircraftList.orEmpty()
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
            .mapValues { (_, group) -> group.sortedBy { AirportBadgeIcons.sortKey(it.facility) } }
    }

    // 출도착 공항 라벨은 충분히 확대했을 때만, 그것도 화면에 들어오는 것만 그립니다.
    // 전 세계 수백 개를 한꺼번에 올리면 축소 화면이 라벨로 뒤덮입니다.
    val visibleAirports = run {
        val zoom = cameraPositionState.position.zoom
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
        remember(state.flightAirports, zoom, bounds) {
            if (zoom < AIRPORT_LABEL_MIN_ZOOM) emptyList()
            else state.flightAirports.filter {
                bounds == null || bounds.contains(LatLng(it.latitude, it.longitude))
            }
        }
    }

    // 클릭 리스너가 항상 최신 목록을 보도록 합니다 (리스너는 1회만 설치됨).
    val latestAircraft by rememberUpdatedState(aircraftList)
    val latestBadges by rememberUpdatedState(badgeGroups)

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

            // 3) 선택한 항공기의 항로 — 대권 경로로 그립니다.
            if (state.routeFlown.size >= 2) {
                Polyline(
                    points = state.routeFlown,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    width = 6f,
                    geodesic = true
                )
            }
            if (state.routeRemaining.size >= 2) {
                Polyline(
                    points = state.routeRemaining,
                    color = MaterialTheme.colorScheme.primary,
                    width = 6f,
                    geodesic = true,
                    // 남은 구간은 점선으로 구분합니다.
                    pattern = listOf(Dash(30f), Gap(20f))
                )
            }
            state.routeEndpoints.forEach { endpoint ->
                Circle(
                    center = endpoint,
                    radius = 12000.0,
                    strokeColor = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4f,
                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            }

            // 4) 항공기와 공항 관제석 배지 — Compose 밖에서 마커를 직접 관리합니다.
            //    클러스터링 없이 전부 표시하므로 마커 재사용이 중요하고,
            //    클릭 리스너를 한 곳에 모아야 배지 클릭이 정상 동작합니다.
            MapEffect(aircraftList, badgeGroups, visibleAirports) { map ->
                // 리스너는 한 번만 설치되므로 목록을 클로저에 그대로 담으면 안 됩니다.
                // 15초마다 새 목록이 만들어지면 리스너는 첫 스냅샷만 계속 보게 되어
                // 그 뒤에 접속한 관제소·항공기를 눌러도 아무 일도 일어나지 않습니다.
                markerController.installClickListener(
                    map = map,
                    onAircraft = { callsign ->
                        latestAircraft.firstOrNull { it.callsign == callsign }
                            ?.let { viewModel.selectAircraft(it) }
                    },
                    onBadge = { airport ->
                        latestBadges[airport]?.let { viewModel.selectControllers(it) }
                    },
                    onAirport = { icao -> viewModel.selectAirport(icao) }
                )
                markerController.syncAircraft(map, aircraftList, state.ownCid, state.ownTierColor)
                markerController.syncBadges(map, badgeGroups)
                markerController.syncAirports(map, visibleAirports)
            }
        }

        MapOverlay(
            state = state,
            onToggleAircraft = viewModel::toggleAircraft,
            onToggleControllers = viewModel::toggleControllers,
            modifier = Modifier.align(Alignment.TopStart)
        )

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        val aircraft = state.selectedAircraft
        val controllers = state.selectedControllers

        if (aircraft != null || controllers.isNotEmpty() || state.selectedAirport != null) {
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissSheet,
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 기상까지 붙으면 길어질 수 있어, 화면 절반을 넘으면 안에서 스크롤합니다.
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.55f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (aircraft != null) {
                        AircraftDetails(
                            aircraft = aircraft,
                            estimatedArrival = state.estimatedArrival,
                            etaIsLive = state.etaIsLive,
                            onAirportClick = viewModel::loadWeather
                        )
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

/** 지도 위 오버레이는 항공기·관제사 표시 토글 두 개만 둡니다. */
@Composable
private fun MapOverlay(
    state: MapUiState,
    onToggleAircraft: () -> Unit,
    onToggleControllers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MapToggleChip(
            label = stringResource(R.string.aircraft_count, state.snapshot?.aircraftList?.size ?: 0),
            icon = Icons.Default.Flight,
            enabled = state.showAircraft,
            onClick = onToggleAircraft
        )
        MapToggleChip(
            label = stringResource(R.string.controller_count, state.snapshot?.controllerList?.size ?: 0),
            icon = Icons.Default.Headset,
            enabled = state.showControllers,
            onClick = onToggleControllers
        )
    }
}

/**
 * 감싸는 카드 없이 지도 위에 바로 놓이므로, 칩 자체가 불투명한 배경을 가져야
 * 밝은 지도 타일 위에서도 글자가 읽힙니다.
 */
@Composable
private fun MapToggleChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) },
        shape = RoundedCornerShape(20.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            leadingIconContentColor = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        ),
        border = null,
        elevation = AssistChipDefaults.assistChipElevation(elevation = 3.dp)
    )
}

/**
 * 항공기 상세.
 * 지도를 최대한 가리지 않도록 조밀하게 배치합니다 — 콜사인과 기종을 한 줄에,
 * 수치 세 개를 한 줄에, 항로 원문은 두 줄로 잘라서 보여줍니다.
 */
@Composable
private fun AircraftDetails(
    aircraft: Aircraft,
    estimatedArrival: String?,
    etaIsLive: Boolean,
    onAirportClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(aircraft.callsign, style = MaterialTheme.typography.titleLarge)
        Text(
            aircraft.aircraftType ?: stringResource(R.string.unknown_type),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Text(
        aircraft.pilotName,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CompactStat(stringResource(R.string.altitude), "%,d ft".format(aircraft.altitude))
        CompactStat(stringResource(R.string.ground_speed), "${aircraft.groundSpeed} kt")
        CompactStat(
            stringResource(R.string.departure_time),
            formatZuluHhmm(aircraft.plannedDepartureHhmm) ?: "—"
        )
        CompactStat(
            // 실시간 계산인지 비행계획상 예정인지 구분해 라벨을 붙입니다.
            stringResource(if (etaIsLive) R.string.eta_live else R.string.eta_planned),
            estimatedArrival ?: "—"
        )
    }

    if (aircraft.departure != null || aircraft.arrival != null) {
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
    }

    aircraft.route?.takeIf { it.isNotBlank() }?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 라벨을 값 아래에 작게 두어 세로 높이를 줄인 통계 표시. */
@Composable
private fun CompactStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

/**
 * 공항 라벨을 표시하기 시작하는 배율.
 * 이보다 낮으면 국가 단위 화면이라 라벨이 서로 겹쳐 읽을 수 없습니다.
 */
private const val AIRPORT_LABEL_MIN_ZOOM = 6f

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
