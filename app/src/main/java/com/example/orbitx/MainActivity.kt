package com.example.orbitx

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.orbitx.ui.theme.OrbitXTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrbitRuntime.init(this)
        val data = OrbitRuntime.data
        val vehicle = OrbitRuntime.vehicle
        val missionNavigation = OrbitRuntime.navigation
        // Ask on first launch: charging suggestions and arrival notices appear while navigation is in front.
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            OrbitXTheme {
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    OrbitApp(data, vehicle?.parking ?: ParkingStatus.Unavailable, vehicle, missionNavigation) {
                        if (vehicle?.parking == ParkingStatus.Parked) missionNavigation?.linkCurrentDestination()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        OrbitRuntime.uiVisible = true
        if (OrbitRuntime.vehicle != null) {
            // The service keeps sampling after navigation comes to the front; fall back to sampling here if it can't start.
            DriveSessionService.start(this)
            OrbitRuntime.startSampling()
        }
        OrbitRuntime.navigation?.refreshRoute()
    }

    override fun onStop() {
        OrbitRuntime.uiVisible = false
        if (!OrbitRuntime.serviceRunning) OrbitRuntime.stopSampling()
        super.onStop()
    }
}
