package com.example.orbitx

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * A short heads-up over navigation when hard acceleration or braking keeps happening on a drive. It shows
 * only after several events add up (see [OrbitData.takeDrivingTip]) and disappears by itself after a few
 * seconds, so it informs without nagging or needing a tap while driving.
 */
internal object DrivingTip {
    private const val CHANNEL = "driving-tip"
    private const val NOTIFICATION_ID = 1005
    private const val VisibleMs = 3_000L

    fun post(context: Context, hardAccel: Int, hardBrake: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "주행 습관 안내", NotificationManager.IMPORTANCE_HIGH))
        manager.notify(NOTIFICATION_ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.orbit_launcher_mark)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setContentTitle("급가속 ${hardAccel}회 · 급제동 ${hardBrake}회")
            .setContentText("전비에 악영향을 줄 수 있어요. 부드럽게 안전운전하세요.")
            .setTimeoutAfter(VisibleMs)
            .setAutoCancel(true)
            .build())
    }
}
