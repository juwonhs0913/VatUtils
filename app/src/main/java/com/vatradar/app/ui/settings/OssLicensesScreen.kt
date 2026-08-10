package com.vatradar.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vatradar.app.R

/**
 * 빌드에 실제로 들어간 오픈소스 라이브러리와 그 라이선스 원문.
 *
 * 200개가 넘고 원문이 300KB라 한 번에 다 그리면 화면이 버벅입니다.
 * 이름만 목록으로 두고, 누른 것만 본문을 읽어 폅니다.
 */
@Composable
fun OssLicensesScreen() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<OssLicenses.Entry>?>(null) }
    var openName by remember { mutableStateOf<String?>(null) }
    var openText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { entries = OssLicenses.entries(context) }

    val loaded = entries
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (loaded.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                stringResource(R.string.apache_two_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(loaded, key = { it.name }) { entry ->
            val open = openName == entry.name
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openName = if (open) null else entry.name }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                    AnimatedVisibility(visible = open) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider()
                            Text(
                                openText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // 펼친 항목의 본문만 읽습니다.
    LaunchedEffect(openName) {
        val entry = loaded.firstOrNull { it.name == openName }
        openText = if (entry == null) "" else OssLicenses.text(context, entry)
    }
}
