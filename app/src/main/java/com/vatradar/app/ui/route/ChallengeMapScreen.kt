package com.vatradar.app.ui.route

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.vatradar.app.R
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.distanceNmTo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ChallengeMapViewModel(app: Application) : AndroidViewModel(app) {

    private val airportRepo = ServiceLocator.airportRepository(app)

    private val _pair = MutableStateFlow<Pair<Airport, Airport>?>(null)
    val pair = _pair.asStateFlow()

    fun load(origin: String, destination: String) {
        viewModelScope.launch {
            val from = airportRepo.find(origin)
            val to = airportRepo.find(destination)
            if (from != null && to != null) _pair.value = from to to
        }
    }
}

/**
 * 뽑힌 경로를 지도에서 한눈에 보여줍니다.
 *
 * 두 공항이 화면에 함께 들어오도록 카메라를 경계 상자에 맞춥니다. 태평양을 건너는
 * 구간은 경계 상자가 지구 절반을 덮어 크게 축소되는데, 그게 실제 거리감이라
 * 억지로 당기지 않습니다.
 */
@OptIn(com.google.maps.android.compose.GoogleMapComposable::class)
@Composable
fun ChallengeMapScreen(
    origin: String,
    destination: String,
    viewModel: ChallengeMapViewModel = viewModel()
) {
    LaunchedEffect(origin, destination) { viewModel.load(origin, destination) }
    val pair by viewModel.pair.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        val from = pair?.first
        val to = pair?.second

        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$origin → $destination", style = MaterialTheme.typography.headlineSmall)
                if (from != null && to != null) {
                    Text(
                        "%,d nm".format(from.distanceNmTo(to).roundToInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${from.name} → ${to.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(LatLng(20.0, 0.0), 1f)
            }

            if (from != null && to != null) {
                val a = LatLng(from.latitude, from.longitude)
                val b = LatLng(to.latitude, to.longitude)

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(mapType = MapType.NORMAL),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                ) {
                    // 카메라 맞추기는 MapEffect로 합니다. cameraPositionState.animate를
                    // 컴포지션 중에 부르면 지도가 아직 배치되기 전이라 조용히 실패합니다.
                    MapEffect(a, b) { map ->
                        val bounds = LatLngBounds.builder().include(a).include(b).build()
                        runCatching {
                            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140))
                        }
                    }

                    Polyline(
                        points = listOf(a, b),
                        color = Color(0xFF3F6FB5),
                        width = 6f,
                        geodesic = true      // 대권 경로라야 실제 비행 궤적과 같습니다
                    )
                    Marker(
                        state = rememberMarkerState(position = a),
                        title = from.icao,
                        snippet = from.name
                    )
                    Marker(
                        state = rememberMarkerState(position = b),
                        title = to.icao,
                        snippet = to.name
                    )
                }
            } else {
                Text(
                    stringResource(R.string.loading),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

