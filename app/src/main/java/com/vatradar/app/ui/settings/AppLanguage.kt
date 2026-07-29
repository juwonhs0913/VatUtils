package com.vatradar.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * 앱에서 고를 수 있는 언어.
 *
 * 영어·한국어·중국어 외 두 언어는 VATSIM 디비전 규모를 기준으로 골랐습니다.
 * VATGER(독일)와 VATBRZ(브라질)가 비영어권에서 가장 큰 커뮤니티입니다.
 *
 * 표시 이름은 항상 그 언어 자체로 씁니다 — 모르는 언어로 적힌 목록에서
 * 자기 언어를 찾는 건 어려우니까요.
 */
enum class AppLanguage(val tag: String, val displayName: String) {
    SYSTEM("", "System default"),
    ENGLISH("en", "English"),
    KOREAN("ko", "한국어"),
    CHINESE("zh", "中文"),
    GERMAN("de", "Deutsch"),
    PORTUGUESE("pt", "Português");

    companion object {
        fun fromTag(tag: String): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: SYSTEM

        /**
         * 선택한 언어를 적용합니다.
         * AppCompat이 Android 13 미만에서도 앱별 언어를 백포트해 줍니다.
         */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                if (language == SYSTEM) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(language.tag)
            )
        }
    }
}
