package com.vatradar.app.ui.route

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.browser.customtabs.CustomTabsIntent
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.Continent
import com.vatradar.app.domain.model.OfpSummary
import com.vatradar.app.ui.weather.WeatherSection
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(viewModel: RouteViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.reloadSettings() }

    // SimBrief 디스패치 페이지 열기 (F5)
    LaunchedEffect(state.dispatchUrl) {
        state.dispatchUrl?.let { url ->
            CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
            viewModel.consumeDispatchUrl()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("무작위 경로 생성", style = MaterialTheme.typography.headlineSmall)

        // ---------------- 필터 ----------------
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("대륙", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.continent == null,
                        onClick = { viewModel.setContinent(null) },
                        label = { Text("전체") }
                    )
                    Continent.entries.forEach { c ->
                        FilterChip(
                            selected = state.continent == c.code,
                            onClick = { viewModel.setContinent(c.code) },
                            label = { Text(c.label) }
                        )
                    }
                }

                // 국가 드롭다운
                var expanded by remember { mutableStateOf(false) }
                val selectedCountryName = state.countries
                    .firstOrNull { it.code == state.country }?.name ?: "전체"

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCountryName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("국가") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("전체") },
                            onClick = { viewModel.setCountry(null); expanded = false }
                        )
                        state.countries.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.name} (${c.code})") },
                                onClick = { viewModel.setCountry(c.code); expanded = false }
                            )
                        }
                    }
                }

                // 최소 활주로 길이 — PRD의 핵심 제약 조건
                Text(
                    "최소 활주로 길이: ${"%,d".format(state.minRunwayFt)} ft " +
                        "(${"%,d".format((state.minRunwayFt * 0.3048).roundToInt())} m)",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = state.minRunwayFt.toFloat(),
                    onValueChange = { viewModel.setMinRunway((it / 500).roundToInt() * 500) },
                    valueRange = 2000f..14000f,
                    steps = 23
                )
                Text(
                    "B777·A350급은 보통 8,000ft 이상이 필요합니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("포장 활주로만", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.hardSurfaceOnly,
                        onCheckedChange = viewModel::setHardSurfaceOnly
                    )
                }

                Text(
                    "조건 충족 공항: ${"%,d".format(state.candidateCount)}곳",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.candidateCount < 2) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = viewModel::roll,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.rolling && state.candidateCount >= 2
        ) {
            if (state.rolling) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Casino, null, Modifier.size(18.dp))
            }
            Text("  경로 뽑기", style = MaterialTheme.typography.titleMedium)
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        // ---------------- 결과 ----------------
        val origin = state.origin
        val destination = state.destination
        if (origin != null && destination != null) {
            AirportCard("출발", origin)
            AirportCard("도착", destination)

            // F5 SimBrief
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("SimBrief 비행 계획", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "기종 ${state.aircraftType}" +
                            (state.airline.takeIf { it.isNotBlank() }?.let { " · 항공사 $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(onClick = viewModel::prepareDispatch, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                        Text("  SimBrief에서 생성하기")
                    }
                    Text(
                        "SimBrief 페이지가 열리면 Generate를 눌러 OFP를 만든 뒤, 아래 버튼으로 결과를 가져옵니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = viewModel::fetchOfp,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.ofpLoading
                    ) {
                        if (state.ofpLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Description, null, Modifier.size(18.dp))
                        }
                        Text("  생성된 OFP 가져오기")
                    }

                    state.ofpError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            state.ofp?.let { OfpCard(it) }

            // F6 기상
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    WeatherSection(report = state.originWeather, loading = state.weatherLoading)
                    WeatherSection(report = state.destinationWeather, loading = false)
                }
            }
        }
    }
}

@Composable
private fun AirportCard(role: String, airport: Airport) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(role, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "${airport.icao}${airport.iata.takeIf { it.isNotBlank() }?.let { " / $it" } ?: ""}",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(airport.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${airport.countryName} · 최장 활주로 ${"%,d".format(airport.maxRunwayFt)}ft " +
                    "(${"%,d".format(airport.maxRunwayMeters)}m) · 표고 ${airport.elevationFt}ft",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OfpCard(ofp: OfpSummary) {
    val context = LocalContext.current
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OFP 요약", style = MaterialTheme.typography.titleMedium)
            Text(
                "${ofp.flightNumber}  ${ofp.origin} → ${ofp.destination}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(ofp.aircraft, style = MaterialTheme.typography.bodySmall)

            OfpRow("순항 고도", ofp.cruiseAltitude)
            OfpRow("블록 연료", ofp.blockFuel)
            OfpRow("순항 소모", ofp.enrouteBurn)
            OfpRow("예상 비행시간", ofp.timeEnroute)
            OfpRow("거리", ofp.distanceNm)
            OfpRow("코스트 인덱스", ofp.costIndex)

            Text("항로", style = MaterialTheme.typography.labelMedium)
            Text(
                ofp.route,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )

            ofp.pdfUrl?.let { url ->
                Button(
                    onClick = { CustomTabsIntent.Builder().build().launchUrl(context, url.toUri()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp))
                    Text("  OFP PDF 열기")
                }
            }
        }
    }
}

@Composable
private fun OfpRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
