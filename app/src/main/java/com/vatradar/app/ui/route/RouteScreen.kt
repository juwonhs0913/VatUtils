package com.vatradar.app.ui.route

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vatradar.app.R
import com.vatradar.app.data.local.ChallengeEntity
import com.vatradar.app.data.repository.ChallengeRepository
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.Continent
import com.vatradar.app.domain.model.OfpSummary
import com.vatradar.app.ui.weather.WeatherSection

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteScreen(
    viewModel: RouteViewModel = viewModel(),
    onOpenChallengeMap: (String, String) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val missingId = stringResource(R.string.simbrief_id_required)

    LaunchedEffect(Unit) {
        viewModel.reloadSettings()
        viewModel.syncFlightProgress()
    }

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
        if (state.vatsimCid.isBlank()) {
            Text(
                stringResource(R.string.cid_needed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        state.activeChallenges.forEach { challenge ->
            ActiveChallengeCard(
                challenge = challenge,
                onOpenMap = { onOpenChallengeMap(challenge.origin, challenge.destination) },
                onDelete = { viewModel.deleteChallenge(challenge.id) }
            )
        }

        Text(stringResource(R.string.random_route), style = MaterialTheme.typography.headlineSmall)

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.pick_region),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.filter.continent == null,
                        onClick = { viewModel.setContinent(null) },
                        label = { Text(stringResource(R.string.worldwide)) }
                    )
                    Continent.entries.forEach { continent ->
                        FilterChip(
                            selected = state.filter.continent == continent.code,
                            onClick = { viewModel.setContinent(continent.code) },
                            label = { Text(continent.displayName) }
                        )
                    }
                }

                // 대륙을 고른 뒤에만 나라를 좁힐 수 있게 합니다.
                // 전 세계 나라를 한 번에 펼치면 200개가 넘어 고르기 어렵습니다.
                if (state.filter.continent != null && state.countries.isNotEmpty()) {
                    var countriesExpanded by remember(state.filter.continent) { mutableStateOf(false) }
                    val selectedName = state.countries
                        .firstOrNull { it.first == state.filter.country }?.second

                    TextButton(
                        onClick = { countriesExpanded = !countriesExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            selectedName ?: stringResource(R.string.all_countries),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (countriesExpanded) Icons.Default.ExpandLess
                            else Icons.Default.ExpandMore,
                            null,
                            Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(visible = countriesExpanded) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.filter.country == null,
                                onClick = { viewModel.setCountry(null); countriesExpanded = false },
                                label = { Text(stringResource(R.string.all_countries)) }
                            )
                            state.countries.forEach { (code, name) ->
                                FilterChip(
                                    selected = state.filter.country == code,
                                    onClick = { viewModel.setCountry(code); countriesExpanded = false },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                }

                Text(
                    stringResource(R.string.real_route_pool, "%,d".format(state.routePoolSize)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = viewModel::roll,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.rolling
        ) {
            if (state.rolling) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Casino, null, Modifier.size(18.dp))
            }
            Text("  " + stringResource(R.string.roll_route), style = MaterialTheme.typography.titleMedium)
        }

        state.error?.let { code ->
            Text(
                stringResource(R.string.roll_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        val route = state.route
        if (route != null) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "${route.origin.icao} → ${route.destination.icao}",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        "${stringResource(R.string.distance)} ${"%,d".format(route.distanceNm)} nm",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    route.airline?.let { airline ->
                        Text(
                            stringResource(R.string.operated_by, airline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AirportCard(stringResource(R.string.departure), route.origin)
            AirportCard(stringResource(R.string.arrival), route.destination)

            // F5 SimBrief
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(stringResource(R.string.simbrief_plan), style = MaterialTheme.typography.titleMedium)

                    Button(onClick = viewModel::prepareDispatch, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                        Text("  " + stringResource(R.string.generate_on_simbrief))
                    }
                    Text(
                        stringResource(R.string.simbrief_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { viewModel.fetchOfp(missingId) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.ofpLoading
                    ) {
                        if (state.ofpLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Description, null, Modifier.size(18.dp))
                        }
                        Text("  " + stringResource(R.string.fetch_ofp))
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
private fun ActiveChallengeCard(
    challenge: ChallengeEntity,
    onOpenMap: () -> Unit,
    onDelete: () -> Unit
) {
    Card(onClick = onOpenMap) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    stringResource(R.string.active_challenge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                // 카드 전체가 지도 열기라, X는 클릭이 카드로 새지 않도록 따로 받습니다.
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onDelete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${challenge.origin} → ${challenge.destination}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "%,d nm".format(challenge.distanceNm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(
                    if (challenge.seenEnroute) R.string.challenge_enroute
                    else R.string.challenge_waiting
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (challenge.seenEnroute) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.tap_to_see_map),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
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
                "${airport.countryName} · ${stringResource(R.string.longest_runway)} " +
                    "${"%,d".format(airport.maxRunwayFt)}ft (${"%,d".format(airport.maxRunwayMeters)}m) · " +
                    "${stringResource(R.string.elevation)} ${airport.elevationFt}ft",
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
            Text(stringResource(R.string.ofp_summary), style = MaterialTheme.typography.titleMedium)
            Text(
                "${ofp.flightNumber}  ${ofp.origin} → ${ofp.destination}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(ofp.aircraft, style = MaterialTheme.typography.bodySmall)

            OfpRow(stringResource(R.string.cruise_altitude), ofp.cruiseAltitude)
            OfpRow(stringResource(R.string.block_fuel), ofp.blockFuel)
            OfpRow(stringResource(R.string.enroute_burn), ofp.enrouteBurn)
            OfpRow(stringResource(R.string.time_enroute), ofp.timeEnroute)
            OfpRow(stringResource(R.string.distance), ofp.distanceNm)
            OfpRow(stringResource(R.string.cost_index), ofp.costIndex)

            Text(stringResource(R.string.route_label), style = MaterialTheme.typography.labelMedium)
            Text(ofp.route, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)

            ofp.pdfUrl?.let { url ->
                Button(
                    onClick = { CustomTabsIntent.Builder().build().launchUrl(context, url.toUri()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp))
                    Text("  " + stringResource(R.string.open_ofp_pdf))
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
