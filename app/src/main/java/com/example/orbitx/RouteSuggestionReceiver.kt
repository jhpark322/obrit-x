package com.example.orbitx

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the driving-time route suggestion actions without bringing ORBIT X to the front. */
class RouteSuggestionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val navigation = MissionNavigation.active ?: return
        when (intent.action) {
            ACTION_CHARGERS -> navigation.onSuggestionAction(accepted = true)
            ACTION_LATER -> navigation.onSuggestionAction(accepted = false)
        }
    }

    companion object {
        const val ACTION_CHARGERS = "com.example.orbitx.action.ROUTE_CHARGERS"
        const val ACTION_LATER = "com.example.orbitx.action.ROUTE_LATER"
    }
}
