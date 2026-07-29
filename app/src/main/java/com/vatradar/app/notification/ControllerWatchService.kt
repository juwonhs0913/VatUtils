package com.vatradar.app.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 60초 간격 관심 관제소 감시 (실시간 모드).
 *
 * WorkManager 주기 작업은 Android 제약으로 최소 15분이라, 그보다 촘촘히 보려면
 * 포그라운드 서비스밖에 방법이 없습니다. 그래서 상시 알림이 하나 뜹니다 —
 * 이건 Android가 강제하는 것이지 선택 사항이 아닙니다.
 *
 * 알아둘 제약:
 *  - Android 15부터 dataSync 유형 포그라운드 서비스는 하루 누적 6시간으로 제한됩니다.
 *    한도에 걸리면 시스템이 서비스를 내리고, 이때는 절전 모드로 돌아가야 합니다.
 *  - Android 14부터 dataSync 유형은 부팅 완료 시점에 시작할 수 없습니다.
 *    재부팅 후에는 앱을 한 번 열어야 감시가 복구됩니다.
 */
class ControllerWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()

        if (pollJob == null) {
            pollJob = scope.launch {
                while (isActive) {
                    runCatching { ControllerWatcher.checkOnce(applicationContext) }
                        .onFailure { Log.w("VATRadar", "관제소 감시 실패", it) }
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        // 시스템이 서비스를 죽여도 다시 살아나도록 합니다.
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notification = Notifications.buildWatchingNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notifications.WATCHING_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Notifications.WATCHING_ID, notification)
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val POLL_INTERVAL_MS = 60_000L

        /**
         * 백그라운드에서 포그라운드 서비스를 시작하면 시스템이 거부할 수 있습니다.
         * 앱이 화면에 있을 때 호출되는 게 정상 경로이고, 그 외에는 조용히 넘어갑니다.
         */
        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, ControllerWatchService::class.java))
            }.onFailure { Log.w("VATRadar", "실시간 감시를 시작하지 못했습니다", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ControllerWatchService::class.java))
        }
    }
}
