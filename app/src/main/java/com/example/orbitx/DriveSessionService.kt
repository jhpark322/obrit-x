package com.example.orbitx

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps vehicle sampling and the Pleos navigation link running while navigation is in front,
 * so the efficiency league, charging suggestions and automatic arrival work during a real drive.
 * Stops itself once ORBIT X is closed and the car has been parked with nothing to watch for a while.
 */
class DriveSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        OrbitRuntime.init(this)
        val started = runCatching { goForeground() }
            .onFailure { Log.w(TAG, "Unable to run in the foreground", it) }
            .isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        OrbitRuntime.serviceRunning = true
        OrbitRuntime.startSampling()
        if (!watching) {
            watching = true
            scope.launch {
                var idleSince = 0L
                while (isActive) {
                    delay(CheckIntervalMs)
                    val idle = !OrbitRuntime.uiVisible && OrbitRuntime.isIdle()
                    val now = System.currentTimeMillis()
                    idleSince = if (idle) idleSince.takeIf { it > 0 } ?: now else 0L
                    if (idleSince > 0 && now - idleSince >= IdleStopMs) {
                        stopSelf()
                        break
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, "주행 기록", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.orbit_launcher_mark)
            .setContentTitle("ORBIT X 주행 기록 중")
            .setContentText("주행 효율과 내비 도착을 앱을 열지 않아도 기록해요.")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        OrbitRuntime.serviceRunning = false
        // The activity may still be on screen (e.g. the system stopped us); keep it working.
        if (!OrbitRuntime.uiVisible) OrbitRuntime.stopSampling()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OrbitDriveSession"
        private const val CHANNEL = "drive-session"
        private const val NOTIFICATION_ID = 1003
        private const val CheckIntervalMs = 30_000L
        private const val IdleStopMs = 10 * 60_000L

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, DriveSessionService::class.java)) }
                .onFailure { Log.w(TAG, "Unable to start drive session", it) }
        }
    }
}
