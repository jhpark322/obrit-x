package com.example.orbitx

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-wide owner of the vehicle and navigation connections.
 *
 * While driving, Pleos navigation sits in front and ORBIT X is in the background, so sampling must not
 * depend on the activity being visible. [DriveSessionService] keeps the process alive and calls
 * [startSampling]; the activity only renders what this object holds.
 */
internal object OrbitRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sampling: Job? = null

    lateinit var data: OrbitData
        private set
    private lateinit var appContext: Context
    var vehicle: VehicleAccess? = null
        private set
    var navigation: MissionNavigation? = null
        private set

    /** True while an ORBIT X screen is on display. */
    var uiVisible = false

    /** True while [DriveSessionService] runs in the foreground and owns sampling. */
    var serviceRunning = false

    val isSampling get() = sampling?.isActive == true

    fun init(context: Context) {
        if (::data.isInitialized) return
        val app = context.applicationContext
        appContext = app
        data = OrbitData(app)
        if (app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            val source = VehicleAccess(app, scope)
            vehicle = source
            navigation = MissionNavigation(app, scope, data) { source.parking == ParkingStatus.Parked }
        }
    }

    fun startSampling() {
        val source = vehicle ?: return
        if (isSampling) return
        source.start()
        sampling = scope.launch {
            while (isActive) {
                data.recordLeagueSample(source.readLeagueSample())
                navigation?.vehicleBatteryPercent = source.readBatteryPercent()
                // After arrival, once parked, ask the drive's experiment question over navigation.
                if (!uiVisible && source.parking == ParkingStatus.Parked) ExperimentPrompt.maybePost(appContext, data)
                delay(SampleIntervalMs)
            }
        }
        navigation?.restoreMissionLink()
        // Stay connected to Pleos navigation so route start and arrival are picked up without extra taps.
        navigation?.refreshRoute()
    }

    fun stopSampling() {
        sampling?.cancel()
        sampling = null
        data.recordLeagueSample(null)
        vehicle?.stop()
        navigation?.release()
    }

    /** Nothing left to watch: parked, no drive running and no guidance in progress. */
    fun isIdle(): Boolean {
        val parked = vehicle?.parking.let { it == null || it == ParkingStatus.Parked || it == ParkingStatus.Unavailable }
        return parked && data.activeMission == null && navigation?.route == null
    }

    private const val SampleIntervalMs = 3_000L
}
