package com.vatradar.app.notification

import androidx.annotation.StringRes
import com.vatradar.app.R

/**
 * 관심 관제소를 어떻게 감시할지.
 *
 * 서버(FCM) 없이 무료로 쓸 수 있는 두 가지 경로입니다.
 * FCM을 설정하면 서버가 훨씬 짧은 주기로 감지해 푸시하고, 이 설정은 백업으로 남습니다.
 */
enum class WatchMode(
    val tag: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int
) {
    /** WorkManager 주기 작업. 배터리 부담이 없는 대신 Android 제약으로 최소 15분 간격입니다. */
    BATTERY_SAVER("battery", R.string.watch_mode_battery, R.string.watch_mode_battery_desc),

    /** 포그라운드 서비스. 60초마다 확인하는 대신 상시 알림이 하나 뜹니다. */
    REALTIME("realtime", R.string.watch_mode_realtime, R.string.watch_mode_realtime_desc);

    companion object {
        fun fromTag(tag: String): WatchMode = entries.firstOrNull { it.tag == tag } ?: BATTERY_SAVER
    }
}
