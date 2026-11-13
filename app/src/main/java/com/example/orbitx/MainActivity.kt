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
import androidx.lifecycle.lifecycleScope
import com.example.orbitx.ui.theme.OrbitXTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var data: OrbitData
    private var vehicle: VehicleAccess? = null
    private var leagueSampling: Job? = null
    private var missionNavigation: MissionNavigation? = null
    private var requestedNotifications = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        data = OrbitData(applicationContext)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            vehicle = VehicleAccess(applicationContext, lifecycleScope)
            missionNavigation = MissionNavigation(applicationContext, lifecycleScope, data) {
                vehicle?.parking == ParkingStatus.Parked
            }
        }
        setContent {
            OrbitXTheme {
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    OrbitApp(data, vehicle?.parking ?: ParkingStatus.Unavailable, vehicle, missionNavigation) {
                        if (vehicle?.parking == ParkingStatus.Parked) {
                            missionNavigation?.linkCurrentDestination()
                            if (!requestedNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                requestedNotifications = true
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vehicle?.let { source ->
            source.start()
            leagueSampling = lifecycleScope.launch {
                while (isActive) {
                    data.recordLeagueSample(source.readLeagueSample())
                    missionNavigation?.vehicleBatteryPercent = source.readBatteryPercent()
                    delay(3_000)
                }
            }
        }
        missionNavigation?.restoreMissionLink()
        // Stay connected to Pleos navigation so route start and arrival are picked up without extra taps.
        missionNavigation?.refreshRoute()
    }

    override fun onStop() {
        leagueSampling?.cancel()
        leagueSampling = null
        data.recordLeagueSample(null)
        vehicle?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        missionNavigation?.release()
        super.onDestroy()
    }
}
