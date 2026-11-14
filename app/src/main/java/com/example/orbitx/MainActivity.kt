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
    private val startupPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrbitRuntime.init(this)
        val data = OrbitRuntime.data
        val vehicle = OrbitRuntime.vehicle
        val missionNavigation = OrbitRuntime.navigation
        // Ask on first launch: charging suggestions and arrival notices appear while navigation is in front.
        // Vehicle speed (hard acceleration/braking in the drive report) is a runtime permission in AAOS.
        if (savedInstanceState == null) {
            val missing = listOfNotNull(
                Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
                SpeedPermission,
            ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) startupPermissions.launch(missing.toTypedArray())
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
