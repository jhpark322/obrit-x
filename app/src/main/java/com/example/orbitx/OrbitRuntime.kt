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
            var tick = 0
            while (isActive) {
                val sample = source.readLeagueSample()
                data.recordLeagueSample(sample)
                navigation?.vehicleBatteryKwh = sample?.batteryKwh
                // Pleos navigation can report "route ended" right after "route started" (re-route), and it
                // only pushes route state on change, so re-read it now and then instead of trusting events.
                if (tick++ % RouteRefreshTicks == 0) navigation?.refreshRoute()
                navigation?.vehicleBatteryPercent = source.readBatteryPercent()
                // Pleos navigation's departure-time prediction, checked against the real arrival later.
                data.notePredictedArrival(navigation?.route?.arrivalBatteryKwh)
                // After arrival, once parked, ask the drive's experiment question over navigation.
                if (!uiVisible && source.parking == ParkingStatus.Parked) ExperimentPrompt.maybePost(appContext, data)
                delay(SampleIntervalMs)
            }
        }
        navigation?.restoreMissionLink()
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
    /** Route state is re-read every 4 samples (12 s); tick 0 connects to navigation right away. */
    private const val RouteRefreshTicks = 4
}
