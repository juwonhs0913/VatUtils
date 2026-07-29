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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.vatradar.app.domain.model.Aircraft
import com.vatradar.app.domain.model.Controller
import com.vatradar.app.domain.model.FacilityType
import com.vatradar.app.ui.weather.WeatherSection

/** 클러스터링에 넣기 위한 래퍼. equals/hashCode가 필요해 data class로 둡니다. */
data class AircraftClusterItem(val aircraft: Aircraft) : ClusterItem {
    override fun getPosition() = LatLng(aircraft.latitude, aircraft.longitude)
    override fun getTitle() = aircraft.callsign
    override fun getSnippet() = "${aircraft.departure ?: "?"} → ${aircraft.arrival ?: "?"}"
    override fun getZIndex() = 0f
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

    // 광역 관제(CTR/FSS)는 FIR 폴리곤으로, 공항 관제는 마커로 표시합니다.
    val boundaryControllers = remember(allControllers) {
        allControllers.filter { it.hasBoundary }
    }
    val markerControllers = remember(allControllers) {
        allControllers.filter { !it.hasBoundary && it.latitude != null && it.longitude != null }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.NORMAL),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
            onMapClick = { viewModel.dismissSheet() }
        ) {
            // 광역 관제 구역: VATSpy FIR 경계 폴리곤
            boundaryControllers.forEach { controller ->
                val color = facilityColor(controller.facility)
                controller.boundary.forEachIndexed { index, ring ->
                    Polygon(
                        points = ring,
                        strokeColor = color,
                        strokeWidth = 3f,
                        fillColor = color.copy(alpha = 0.12f),
                        clickable = true,
                        onClick = { viewModel.selectController(controller) }
                    )
                }
            }

            // 공항 관제 마커: 클러스터링 없이 그대로 (수백 개 수준)
            markerControllers.forEach { controller ->
                val markerState = rememberMarkerState(
                    key = controller.callsign,
                    position = LatLng(controller.latitude!!, controller.longitude!!)
                )
                Marker(
                    state = markerState,
                    title = controller.callsign,
                    snippet = "${controller.frequency} · ${controller.facility.label}",
                    icon = BitmapDescriptorFactory.defaultMarker(facilityHue(controller.facility)),
                    onClick = {
                        viewModel.selectController(controller)
                        true
                    }
                )
            }

            // 항공기: 축소 시 그룹화 (PRD 성능 요구사항).
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

        // 상단 상태 바
        MapOverlay(
            state = state,
            boundaryCount = boundaryControllers.size,
            onToggleAircraft = viewModel::toggleAircraft,
            onToggleControllers = viewModel::toggleControllers,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        val aircraft = state.selectedAircraft
        val controller = state.selectedController

        if (aircraft != null || controller != null) {
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
                    if (controller != null) {
                        ControllerDetails(controller)
                    }
                    WeatherSection(
                        report = state.weather,
                        loading = state.weatherLoading
                    )
                }
            }
        }
    }
}

@Composable
private fun MapOverlay(
    state: MapUiState,
    boundaryCount: Int,
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
                    label = { Text("항공기 ${state.snapshot?.aircraftList?.size ?: 0}") },
                    leadingIcon = { Icon(Icons.Default.Flight, null, Modifier.size(16.dp)) },
                    colors = if (state.showAircraft) AssistChipDefaults.assistChipColors()
                    else AssistChipDefaults.assistChipColors(labelColor = Color.Gray)
                )
                AssistChip(
                    onClick = onToggleControllers,
                    label = { Text("관제사 ${state.snapshot?.controllerList?.size ?: 0}") },
                    leadingIcon = { Icon(Icons.Default.Headset, null, Modifier.size(16.dp)) },
                    colors = if (state.showControllers) AssistChipDefaults.assistChipColors()
                    else AssistChipDefaults.assistChipColors(labelColor = Color.Gray)
                )
                if (state.isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }

            // 관제 시설 범례 — 폴리곤·마커 색을 구분해 읽을 수 있게 합니다.
            if (state.showControllers) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        FacilityType.CTR, FacilityType.FSS, FacilityType.APP,
                        FacilityType.TWR, FacilityType.GND, FacilityType.DEL
                    ).forEach { facility ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            LegendDot(facilityColor(facility))
                            Text(facility.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (boundaryCount > 0) {
                    Text(
                        "관제 구역 $boundaryCount 곳 표시 중 (VATSpy FIR 경계)",
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

/** Google Maps 기본 마커 색상(Hue)으로 매핑 — 시설별 색을 마커에도 반영합니다. */
fun facilityHue(facility: FacilityType): Float = when (facility) {
    FacilityType.CTR -> BitmapDescriptorFactory.HUE_RED
    FacilityType.APP -> BitmapDescriptorFactory.HUE_ORANGE
    FacilityType.TWR -> BitmapDescriptorFactory.HUE_AZURE
    FacilityType.GND -> BitmapDescriptorFactory.HUE_GREEN
    FacilityType.DEL -> BitmapDescriptorFactory.HUE_VIOLET
    FacilityType.FSS -> BitmapDescriptorFactory.HUE_CYAN
    FacilityType.OBS -> BitmapDescriptorFactory.HUE_YELLOW
}

@Composable
private fun AircraftDetails(aircraft: Aircraft, onAirportClick: (String) -> Unit) {
    Text(aircraft.callsign, style = MaterialTheme.typography.headlineSmall)
    Text(
        "${aircraft.pilotName} · ${aircraft.aircraftType ?: "기종 미상"}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        DetailStat("고도", "%,d ft".format(aircraft.altitude))
        DetailStat("대지속도", "${aircraft.groundSpeed} kt")
        DetailStat("기수", "${aircraft.heading.toInt()}°")
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        aircraft.departure?.let {
            AssistChip(onClick = { onAirportClick(it) }, label = { Text("출발 $it") })
        }
        aircraft.arrival?.let {
            AssistChip(onClick = { onAirportClick(it) }, label = { Text("도착 $it") })
        }
    }

    if (aircraft.departure != null || aircraft.arrival != null) {
        Text(
            "공항을 누르면 기상 정보를 확인할 수 있습니다.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    aircraft.route?.takeIf { it.isNotBlank() }?.let {
        Text("항로", style = MaterialTheme.typography.labelMedium)
        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ControllerDetails(controller: Controller) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendDot(facilityColor(controller.facility))
        Text(controller.callsign, style = MaterialTheme.typography.headlineSmall)
    }
    Text(
        "${controller.name} · ${controller.facility.label}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        DetailStat("주파수", controller.frequency)
        DetailStat("가시 범위", "${controller.visualRangeNm} nm")
    }
    controller.airportName?.let {
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
    if (controller.hasBoundary) {
        Text(
            "관제 구역이 지도에 표시되어 있습니다 (VATSpy FIR 경계).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
    if (controller.textAtis.isNotEmpty()) {
        Text("ATIS", style = MaterialTheme.typography.labelMedium)
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

/** 관제 시설 종류별 색 (범례·마커에서 공용) */
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
