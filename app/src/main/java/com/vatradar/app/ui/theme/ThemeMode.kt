package com.vatradar.app.ui.theme

import androidx.annotation.StringRes
import com.vatradar.app.R

enum class ThemeMode(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("system", R.string.theme_system),
    LIGHT("light", R.string.theme_light),
    DARK("dark", R.string.theme_dark);

    companion object {
        fun fromTag(tag: String): ThemeMode = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}
