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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.ConnectingAirports
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.maps.android.compose.CameraPositionState
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.graphics.Brush
import com.vatradar.app.R
import kotlin.math.pow
import com.vatradar.app.domain.model.Aircraft
import com.vatradar.app.domain.BoundaryLabelPoint
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

    // 구역 라벨: 폴리곤이 있는 관제소 + APP 원. 링 좌표 평균을 라벨 위치로 씁니다.
    val zoom = cameraPositionState.position.zoom
    val boundaryLabels = remember(boundaryControllers, approachControllers, zoom) {
        boundaryControllers.mapNotNull { controller ->
            val shape = controller.labelBoundary.ifEmpty { controller.boundary }
            val position = BoundaryLabelPoint.of(shape) ?: return@mapNotNull null
            // 화면에서 너무 좁은 구역은 라벨을 붙이지 않습니다.
            // 일본·유럽처럼 섹터가 잘게 나뉜 곳에서 라벨이 서로 겹쳐 읽을 수 없게 됩니다.
            val widthPx = BoundaryLabelPoint.longitudeSpan(shape) *
                256.0 * 2.0.pow(zoom.toDouble()) / 360.0
            if (widthPx < MIN_LABEL_WIDTH_PX) return@mapNotNull null
            BoundaryLabel(
                callsign = controller.callsign,
                position = position,
                argb = AirportBadgeIcons.facilityArgb(controller.facility)
            )
        } + approachControllers.filter { zoom >= APP_LABEL_MIN_ZOOM }.map { controller ->
            // 원 한가운데 두면 공항 마커·배지와 겹칩니다. 반경만큼 북쪽으로 올려
            // 원 위쪽 테두리 바깥에 태그가 붙게 합니다.
            val offsetDeg = controller.approachRadiusMeters() / 111_320.0
            BoundaryLabel(
                callsign = controller.callsign,
                position = LatLng(
                    (controller.latitude!! + offsetDeg).coerceAtMost(85.0),
                    controller.longitude!!
                ),
                argb = AirportBadgeIcons.facilityArgb(FacilityType.APP)
            )
        }
    }
    val latestBoundaryOwners by rememberUpdatedState(
        (boundaryControllers + approachControllers).associateBy { it.callsign }
    )

    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.NORMAL),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
            onMapClick = { viewModel.dismissSheet() }
        ) {
            // 1) 광역 관제 구역 — FIR 폴리곤
            //    담당 구역만 그립니다. 예전에는 소속 ACC 전역을 옅게 깔아 소속을 보여줬는데,
            //    섹터 하나만 열려 있어도 나라 전체가 붉게 물들어 보여 오해를 샀습니다.
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
            MapEffect(aircraftList, badgeGroups, visibleAirports, boundaryLabels) { map ->
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
                    onAirport = { icao -> viewModel.selectAirport(icao) },
                    onBoundary = { callsign ->
                        latestBoundaryOwners[callsign]?.let { viewModel.selectControllers(listOf(it)) }
                    }
                )
                markerController.syncAircraft(map, aircraftList, state.ownCid)
                markerController.syncBadges(map, badgeGroups)
                markerController.syncBoundaryLabels(map, boundaryLabels)
                markerController.syncAirports(map, visibleAirports)
            }
        }

        MapOverlay(
            state = state,
            onToggleAircraft = viewModel::toggleAircraft,
            onToggleControllers = viewModel::toggleControllers,
            modifier = Modifier.align(Alignment.TopStart)
        )

        MapTools(
            state = state,
            onQuery = viewModel::setSearchQuery,
            onPick = { hit ->
                viewModel.selectSearchResult(hit)
                scope.launch {
                    runCatching {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(hit.latitude, hit.longitude),
                                if (hit.isAircraft) 7f else 5f
                            )
                        )
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // 나침반은 좌하단. Google 로고가 그 자리에 있어 로고 위로 띄웁니다
        // (로고를 가리는 건 지도 이용약관 위반입니다).
        MapToolButton(
            icon = Icons.Default.Explore,
            description = stringResource(R.string.north_up),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 40.dp)
        ) {
            scope.launch {
                runCatching {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder(cameraPositionState.position)
                                .bearing(0f).tilt(0f).build()
                        )
                    )
                }
            }
        }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        val aircraft = state.selectedAircraft
        val controllers = state.selectedControllers

        if (aircraft != null || controllers.isNotEmpty() || state.selectedAirport != null) {
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissSheet,
                sheetState = sheetState
            ) {
                val sheetScroll = rememberScrollState()

                // 아래로 더 있다는 걸 보여주려고 Box로 감쌉니다.
                // 시트가 딱 잘려 보이면 사용자는 그게 전부인 줄 압니다.
                Box(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 기상까지 붙으면 길어지므로 안에서 스크롤합니다.
                        // 상한이 시트가 실제로 차지하는 높이보다 크면 내용이 화면
                        // 아래로 잘려 나가고, 그 자리에 둔 스크롤 표시도 같이 가려집니다.
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.42f)
                        .verticalScroll(sheetScroll)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp),
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
                    if (state.selectedAirport != null) {
                        AirportFlightList(state.selectedAirport!!, state.airportFlights)
                    }
                    WeatherSectionHost(state)
                }

                ScrollMoreHint(
                    visible = sheetScroll.canScrollForward,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                }
            }
        }
    }
}

/**
 * 시트 아래쪽에 "더 있다"는 표시.
 *
 * 내용이 잘린 자리에 배경색으로 흐려지는 띠와 아래 화살표를 얹습니다.
 * 스크롤이 끝까지 내려가면 사라집니다 — 남아 있으면 오히려 거짓말이 됩니다.
 */
@Composable
private fun ScrollMoreHint(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val surface = MaterialTheme.colorScheme.surfaceContainerLow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(surface.copy(alpha = 0f), surface)
                    )
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.scroll_for_more),
                modifier = Modifier.padding(bottom = 4.dp).size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
/** 라벨을 붙일 최소 폭(픽셀). 이보다 좁으면 글자가 구역을 덮어 오히려 방해가 됩니다. */
private const val MIN_LABEL_WIDTH_PX = 55.0

/**
 * 어프로치 이름표를 붙이기 시작하는 줌.
 *
 * 어프로치 공역은 반경 수십 해리라 세계 지도에서는 점입니다. 그 위에 이름표만
 * 남으면 정작 넓은 관제 구역 이름이 가려집니다. 멀리서는 관제 구역만,
 * 가까이 가면 어프로치까지 보이게 합니다.
 */
private const val APP_LABEL_MIN_ZOOM = 6f

private fun Controller.approachRadiusMeters(): Double {
    // 관제사가 넉넉히 잡아 두는 값이라 그대로 그리면 원이 지도를 덮습니다.
    // 실제 접근 관제 담당 범위에 가깝도록 줄여서 그립니다.
    val nm = visualRangeNm.coerceIn(12, 60)
    return nm * 0.45 * 1852.0
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
            icon = Icons.Default.ConnectingAirports,
            enabled = state.showAircraft,
            onClick = onToggleAircraft
        )
        MapToggleChip(
            label = stringResource(R.string.controller_count, state.snapshot?.controllerList?.size ?: 0),
            icon = Icons.Default.SupportAgent,
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

/**
 * 우상단 도구 — 검색과 북쪽 정렬.
 *
 * 지도를 두 손가락으로 돌리면 북쪽이 어디인지 헷갈리는데, Google 지도의
 * 나침반 버튼은 회전했을 때만 잠깐 나타나 놓치기 쉽습니다. 항상 눌러 되돌릴 수
 * 있게 따로 둡니다.
 */
@Composable
private fun MapTools(
    state: MapUiState,
    onQuery: (String) -> Unit,
    onPick: (SearchHit) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(12.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MapToolButton(
            icon = if (open) Icons.Default.Close else Icons.Default.Search,
            description = stringResource(R.string.search_traffic)
        ) {
            open = !open
            if (!open) onQuery("")
        }

        if (open) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onQuery,
                        placeholder = { Text(stringResource(R.string.search_traffic)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.searchQuery.isNotBlank()) {
                        if (state.searchHits.isEmpty()) {
                            Text(
                                stringResource(R.string.no_results),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            // 목록이 길어지면 지도를 다 덮으므로 높이를 묶습니다.
                            Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                                state.searchHits.forEach { hit ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onPick(hit)
                                                open = false
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(hit.title, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            hit.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Icon(icon, description, Modifier.padding(10.dp).size(22.dp))
    }
}

/**
 * 공항 라벨을 눌렀을 때의 출도착 목록.
 *
 * 접속 중인 기체만 나옵니다 — VATSIM에는 시간표가 없어서, 지금 그 공항을
 * 출발지나 목적지로 적어 둔 비행계획이 전부입니다.
 */
@Composable
private fun AirportFlightList(icao: String, flights: List<AirportFlight>) {
    Text(
        stringResource(R.string.flights_at, icao),
        style = MaterialTheme.typography.titleSmall
    )

    if (flights.isEmpty()) {
        Text(
            stringResource(R.string.no_results),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    flights.forEach { flight ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (flight.departing) Icons.Default.FlightTakeoff
                else Icons.Default.FlightLand,
                contentDescription = stringResource(
                    if (flight.departing) R.string.departing else R.string.arriving
                ),
                modifier = Modifier.size(18.dp),
                tint = if (flight.departing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.tertiary
            )
            Column(Modifier.weight(1f)) {
                Text(
                    flight.callsign + (flight.aircraftType?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${flight.origin.ifBlank { "?" }} → ${flight.destination.ifBlank { "?" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
