package com.vatradar.app.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vatradar.app.R
import com.vatradar.app.data.repository.WeatherReport
import com.vatradar.app.domain.metar.FlightCategory
import com.vatradar.app.domain.metar.MetarIcon

/**
 * F6: 원문(Raw)과 디코딩된 정보를 함께 보여줍니다.
 * PRD 요구대로 각 항목에 아이콘을 붙여 직관적으로 읽히게 합니다.
 */
@Composable
fun WeatherSection(
    report: WeatherReport?,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    if (loading) {
        Row(
            modifier = modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.loading_weather), style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    if (report == null) return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.weather_of, report.icao), style = MaterialTheme.typography.titleMedium)
            report.metar?.let { FlightCategoryBadge(it.flightCategory) }
        }

        report.metarError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        report.metar?.let { metar ->
            // Raw 원문
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                Text(
                    metar.raw,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }

            // 디코딩 결과
            metar.fields.forEach { field ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = iconFor(field.icon),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        field.label,
                        modifier = Modifier.width(88.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(field.value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        report.taf?.let { taf ->
            Text(stringResource(R.string.taf_forecast), style = MaterialTheme.typography.labelMedium)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                Text(
                    taf,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun FlightCategoryBadge(category: FlightCategory) {
    val (color, label) = when (category) {
        FlightCategory.VFR -> Color(0xFF2E7D32) to "VFR"
        FlightCategory.MVFR -> Color(0xFF1565C0) to "MVFR"
        FlightCategory.IFR -> Color(0xFFD32F2F) to "IFR"
        FlightCategory.LIFR -> Color(0xFF7B1FA2) to "LIFR"
        FlightCategory.UNKNOWN -> Color(0xFF9E9E9E) to "—"
    }
    Surface(color = color, shape = RoundedCornerShape(4.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

private fun iconFor(icon: MetarIcon) = when (icon) {
    MetarIcon.WIND -> Icons.Default.Air
    MetarIcon.VISIBILITY -> Icons.Default.Visibility
    MetarIcon.CLOUD -> Icons.Default.Cloud
    MetarIcon.TEMPERATURE -> Icons.Default.Thermostat
    MetarIcon.PRESSURE -> Icons.Default.Compress
    MetarIcon.WEATHER -> Icons.Default.WaterDrop
    MetarIcon.TIME -> Icons.Default.Schedule
    MetarIcon.RUNWAY -> Icons.Default.Straighten
    MetarIcon.INFO -> Icons.Default.Info
}
