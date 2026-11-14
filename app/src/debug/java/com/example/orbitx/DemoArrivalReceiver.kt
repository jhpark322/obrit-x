package com.example.orbitx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Debug builds only: the emulator's navigation can't arrive by itself, so the demo replays the arrival event. */
class DemoArrivalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MissionNavigation.active?.replayArrivalForDemo()
    }
}
