package com.vatradar.app

import android.app.Application
import android.util.Log
import com.vatradar.app.data.local.AirportSeeder
import com.vatradar.app.data.local.AppDatabase
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.notification.ControllerWatchWorker
import com.vatradar.app.notification.FcmTopics
import com.vatradar.app.notification.Notifications
import com.vatradar.app.ui.settings.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VatRadarApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)

        appScope.launch {
            // 저장된 언어를 먼저 적용합니다. AppCompat이 프로세스 재시작 후에도
            // 로케일을 복원하지만, 최초 설치 직후 등 비어 있는 경우를 대비합니다.
            runCatching {
                val tag = ServiceLocator.settingsRepository(this@VatRadarApp).current().languageTag
                if (tag.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        AppLanguage.apply(AppLanguage.fromTag(tag))
                    }
                }
            }.onFailure { Log.w("VATRadar", "언어 적용 실패", it) }

            // 첫 실행에서만 실제 적재가 일어나고, 이후에는 count() 한 번으로 끝납니다.
            runCatching {
                AirportSeeder.seedIfNeeded(this@VatRadarApp, AppDatabase.get(this@VatRadarApp))
            }.onFailure { Log.e("VATRadar", "공항 DB 시딩 실패", it) }

            // 설정이 켜져 있으면 감시 작업을 복구합니다 (기기 재부팅·앱 업데이트 후 대비).
            runCatching {
                val settings = ServiceLocator.settingsRepository(this@VatRadarApp).current()
                if (settings.notifyEnabled) {
                    ControllerWatchWorker.enable(this@VatRadarApp)
                }

                // 토픽 구독을 매번 다시 걸어줍니다.
                // Firebase를 나중에 설정한 경우, 그 전에 등록해 둔 관제소는
                // 구독 시도가 조용히 실패한 상태로 남아 있습니다.
                // 중복 구독은 FCM이 알아서 무시하므로 그냥 다시 호출하면 됩니다.
                settings.watchedCallsigns.forEach { FcmTopics.subscribe(it) }
            }.onFailure { Log.w("VATRadar", "관제소 감시 복구 실패", it) }
        }
    }
}
