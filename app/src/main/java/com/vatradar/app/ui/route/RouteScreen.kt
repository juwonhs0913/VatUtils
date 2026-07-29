package com.vatradar.app.ui.route

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vatradar.app.R
import com.vatradar.app.domain.model.Airport
import com.vatradar.app.domain.model.HaulRange
import com.vatradar.app.domain.model.OfpSummary
import com.vatradar.app.ui.weather.WeatherSection

@Composable
fun RouteScreen(viewModel: RouteViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val missingId = stringResource(R.string.simbrief_id_required)

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
        Text(stringResource(R.string.random_route), style = MaterialTheme.typography.headlineSmall)

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaulRange.entries.forEach { haul ->
                        FilterChip(
                            selected = state.haul == haul,
                            onClick = { viewModel.setHaul(haul) },
                            label = { Text(stringResource(haul.labelRes())) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(
                    stringResource(state.haul.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    stringResource(
                        R.string.intl_airport_pool,
                        "%,d".format(state.airportPoolSize)
                    ),
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

        state.error?.let {
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
                        "${stringResource(R.string.distance)} ${"%,d".format(route.distanceNm)} nm · " +
                            stringResource(state.haul.labelRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                    Text(
                        stringResource(R.string.aircraft_label, state.aircraftType) +
                            (state.airline.takeIf { it.isNotBlank() }
                                ?.let { " · " + stringResource(R.string.airline_label, it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

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

private fun HaulRange.labelRes(): Int = when (this) {
    HaulRange.SHORT -> R.string.haul_short
    HaulRange.MEDIUM -> R.string.haul_medium
    HaulRange.LONG -> R.string.haul_long
}

private fun HaulRange.descriptionRes(): Int = when (this) {
    HaulRange.SHORT -> R.string.haul_short_desc
    HaulRange.MEDIUM -> R.string.haul_medium_desc
    HaulRange.LONG -> R.string.haul_long_desc
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
