package com.vatradar.app.ui.settings

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.vatradar.app.R

/**
 * 출처와 라이선스.
 *
 * 스토어에 올리려면 남의 저작물을 어떤 조건으로 쓰고 있는지 밝혀야 합니다.
 * 특히 두 가지가 조건부입니다.
 *
 *  - **VAT-Spy Data Project** (관제 구역 경계)는 CC BY-SA 4.0입니다. 출처를 밝히고,
 *    고쳤으면 고쳤다고 적고, 고친 결과물도 같은 라이선스로 내놓아야 합니다.
 *    앱에 넣은 경계는 Douglas-Peucker로 단순화한 것이므로 세 가지 모두 해당합니다.
 *  - **Google Play services**(지도·FCM)는 앱 안에서 법적 고지를 보여 주도록 요구합니다.
 *    고지 원문은 빌드할 때 의존성에서 뽑아 APK에 넣고, [OssLicensesScreen]이 읽어 줍니다.
 *
 * 나머지(OurAirports, Natural Earth, NOAA)는 퍼블릭 도메인이라 의무는 없지만,
 * 어디서 온 데이터인지는 사용자도 알 자격이 있어 함께 적습니다.
 */
private data class DataSource(
    val name: String,
    val license: String,
    val url: String,
    val useRes: Int
)

private val DATA_SOURCES = listOf(
    DataSource(
        name = "VATSIM Network",
        license = "Public data feed",
        url = "https://data.vatsim.net",
        useRes = R.string.src_vatsim_use
    ),
    DataSource(
        name = "VAT-Spy Data Project",
        license = "CC BY-SA 4.0 · modified",
        url = "https://github.com/vatsimnetwork/vatspy-data-project",
        useRes = R.string.src_vatspy_use
    ),
    DataSource(
        name = "OurAirports",
        license = "Public domain",
        url = "https://ourairports.com/data/",
        useRes = R.string.src_ourairports_use
    ),
    DataSource(
        name = "Natural Earth",
        license = "Public domain",
        url = "https://www.naturalearthdata.com",
        useRes = R.string.src_naturalearth_use
    ),
    DataSource(
        name = "NOAA Aviation Weather Center",
        license = "U.S. Government work · public domain",
        url = "https://aviationweather.gov",
        useRes = R.string.src_noaa_use
    ),
    DataSource(
        name = "SimBrief",
        license = "Public API",
        url = "https://www.simbrief.com",
        useRes = R.string.src_simbrief_use
    )
)

/**
 * 화면에 먼저 보여 줄 대표 라이브러리.
 *
 * 전체 목록(200개 남짓)과 라이선스 원문은 빌드가 만든 리소스에 있고, 아래 버튼으로 넘어갑니다.
 * 여기 몇 줄을 두는 이유는 "무엇으로 만들었는지"가 한눈에 보여야 하기 때문입니다.
 */
private val LIBRARIES = listOf(
    "Android Jetpack · Jetpack Compose · Material Components (Google)",
    "Kotlin · kotlinx.coroutines · kotlinx.serialization (JetBrains)",
    "Retrofit · OkHttp (Square)",
    "Coil (Coil Contributors)",
    "Maps Compose · Android Maps Utils (Google)"
)

@Composable
fun LicensesScreen(onOpenOssLicenses: () -> Unit = {}) {
    val context = LocalContext.current

    fun open(url: String) =
        CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.legal_notices),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.vatsim_disclaimer),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.data_sources),
                    style = MaterialTheme.typography.titleMedium
                )
                DATA_SOURCES.forEachIndexed { index, source ->
                    if (index > 0) HorizontalDivider()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { open(source.url) },
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(source.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(source.useRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            source.license,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider()
                Text(
                    stringResource(R.string.vatspy_sharealike),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.open_source_licenses),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.oss_notice_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LIBRARIES.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onOpenOssLicenses, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.show_notices),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}
