package com.vatradar.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val RadarNavy = Color(0xFF0B1D2E)
private val RadarBlue = Color(0xFF1565C0)
private val RadarCyan = Color(0xFF82E0FF)

private val LightColors = lightColorScheme(
    primary = RadarBlue,
    secondary = Color(0xFF00838F),
    tertiary = Color(0xFFF57C00)
)

private val DarkColors = darkColorScheme(
    primary = RadarCyan,
    secondary = Color(0xFF4DD0E1),
    tertiary = Color(0xFFFFB74D),
    background = RadarNavy,
    surface = Color(0xFF12293D)
)

@Composable
fun VatRadarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        // Android 12+ 는 사용자 배경화면 기반 다이내믹 컬러를 우선합니다.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
