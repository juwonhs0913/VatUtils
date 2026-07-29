package com.vatradar.app

import android.app.Application
import android.util.Log
import com.vatradar.app.data.local.AirportSeeder
import com.vatradar.app.data.local.AppDatabase
import com.vatradar.app.di.ServiceLocator
import com.vatradar.app.notification.ControllerWatchWorker
import com.vatradar.app.notification.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VatRadarApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)

        appScope.launch {
            // 첫 실행에서만 실제 적재가 일어나고, 이후에는 count() 한 번으로 끝납니다.
            runCatching {
                AirportSeeder.seedIfNeeded(this@VatRadarApp, AppDatabase.get(this@VatRadarApp))
            }.onFailure { Log.e("VATRadar", "공항 DB 시딩 실패", it) }

            // 설정이 켜져 있으면 감시 작업을 복구합니다 (기기 재부팅·앱 업데이트 후 대비).
            runCatching {
                if (ServiceLocator.settingsRepository(this@VatRadarApp).current().notifyEnabled) {
                    ControllerWatchWorker.enable(this@VatRadarApp)
                }
            }.onFailure { Log.w("VATRadar", "관제소 감시 복구 실패", it) }
        }
    }
}
