package com.example.orbitx

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Asks the drive's X-LAB experiment question as a notification over navigation once the car is parked
 * after arrival, so the driver can answer with one tap without opening ORBIT X. Never shown while driving.
 */
internal object ExperimentPrompt {
    private const val CHANNEL = "experiment-question"
    private const val NOTIFICATION_ID = 1004
    const val ACTION_ANSWER = "com.example.orbitx.action.EXPERIMENT_ANSWER"
    const val EXTRA_EXPERIMENT = "experiment"
    const val EXTRA_ANSWER = "answer"

    fun maybePost(context: Context, data: OrbitData) {
        val experiment = labExperiment(data.tripExperimentId) ?: return
        if (data.tripExperimentAnswer != null || data.tripExperimentPrompted || !data.tripExperimentAskable) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "X-LAB 실험 질문", NotificationManager.IMPORTANCE_HIGH))
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.orbit_launcher_mark)
            // Shown right after a navigation arrival; the car UI only pops navigation-category notices over the map.
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setContentTitle(experiment.driveQuestion)
            .setContentText("X-LAB 실험 %02d · %s".format(experiment.number, experiment.title))
            .setAutoCancel(true)
        // Notifications show at most three actions; every experiment has three short answers that fit a heads-up button.
        experiment.answers.take(3).forEachIndexed { index, answer ->
            val intent = Intent(context, ExperimentAnswerReceiver::class.java).setAction(ACTION_ANSWER)
                .putExtra(EXTRA_EXPERIMENT, experiment.id).putExtra(EXTRA_ANSWER, index)
            val pending = PendingIntent.getBroadcast(
                context, 10 + index, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(Notification.Action.Builder(null, answer, pending).build())
        }
        // Replace any earlier question outright so this one alerts over navigation instead of updating silently.
        manager.cancel(NOTIFICATION_ID)
        manager.notify(NOTIFICATION_ID, builder.build())
        data.markExperimentPrompted()
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}

class ExperimentAnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ExperimentPrompt.ACTION_ANSWER) return
        OrbitRuntime.init(context)
        val data = OrbitRuntime.data
        val experiment = labExperiment(intent.getStringExtra(ExperimentPrompt.EXTRA_EXPERIMENT))
        val answer = experiment?.answers?.getOrNull(intent.getIntExtra(ExperimentPrompt.EXTRA_ANSWER, -1))
        if (experiment != null && answer != null && experiment.id == data.tripExperimentId) data.saveExperimentAnswer(answer)
        ExperimentPrompt.cancel(context)
    }
}
