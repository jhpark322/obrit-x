package com.example.orbitx

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The route Pleos navigation is guiding right now, as reported by NaviHelper. */
/** [arrivalBatteryKwh]: battery energy navigation expects to be left at the destination. */
internal data class RoutePlan(val name: String, val distanceKm: Float?, val minutes: Int?, val arrivalBatteryKwh: Float?)

/** Pleos navigation reports the energy left at the destination in Wh (e.g. 54014 = 54.0 kWh), not %. */
private fun arrivalKwh(wh: Int): Float? = wh.takeIf { it > 0 }?.div(1_000f)

internal data class RecentPlace(
    val name: String, val address: String, val latitude: Double, val longitude: Double,
    val displayLatitude: Double?, val displayLongitude: Double?, val poiId: String?, val poiSubId: String?, val provider: String?,
)

internal class MissionNavigation(
    private val context: Context,
    private val scope: CoroutineScope,
    private val data: OrbitData,
    private val canOpenScreen: () -> Boolean,
) {
    private var helper: NaviHelper? = null
    private var connected = false
    private var connectionTimeout: Job? = null
    private var requestTimeout: Job? = null
    private var requestedDestination: DestinationInfo? = null
    private var requestedMode: NaviMode? = null
    private var pendingScreen: NavigationScreenRequest? = null
    private var screenTimeout: Job? = null
    var screenStatus by mutableStateOf<String?>(null)
        private set
    var isLinking by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    private var routeState by mutableStateOf<RoutePlan?>(null)
    var route: RoutePlan?
        get() = routeState
        private set(value) {
            routeState = value
            value?.let(::maybeSuggestCharging)
        }
    private var suggestedRoute: String? = null
    /** Last battery % read from the Vehicle SDK while ORBIT X was in front (kept while navigation is in front). */
    var vehicleBatteryPercent: Int? = null
        set(value) {
            field = value
            routeState?.let(::maybeSuggestCharging)
        }
    /** Current battery energy, used with [vehicleBatteryPercent] to turn the arrival energy into a %. */
    var vehicleBatteryKwh: Float? = null

    /** Arrival battery as a % of capacity (capacity estimated from the car's current kWh and %). */
    fun arrivalPercent(plan: RoutePlan?): Int? {
        val energy = plan?.arrivalBatteryKwh ?: return null
        val kwh = vehicleBatteryKwh?.takeIf { it > 0f } ?: return null
        val percent = vehicleBatteryPercent?.takeIf { it > 0 } ?: return null
        return (energy / (kwh * 100f / percent) * 100f).toInt().coerceIn(0, 100)
    }
    var recentPlaces by mutableStateOf<List<RecentPlace>?>(null)
        private set
    private val pendingCalls = mutableListOf<(NaviHelper) -> Unit>()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            scope.launch {
                route = RoutePlan(
                    routeStartInfo.name.orEmpty(),
                    routeStartInfo.distance.takeIf { it > 0 }?.div(1_000f),
                    routeStartInfo.duration.takeIf { it > 0 }?.let { (it + 59) / 60 },
                    arrivalKwh(routeStartInfo.remainDestinationChargingValue),
                )
                // No extra taps: a route started in Pleos navigation (from ORBIT X or by hand) starts the
                // recommended drive if none is running, and its destination is linked for automatic arrival.
                if (data.activeMission == null) data.selectDriveMode(data.recommendedDriveMode)
                if (data.linkedMissionDestination == null) linkCurrentDestination()
            }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            scope.launch {
                if (destinationInfo.remainDistance > 0) {
                    route = RoutePlan(
                        destinationInfo.name.orEmpty().ifBlank { route?.name.orEmpty() },
                        destinationInfo.remainDistance / 1_000f,
                        destinationInfo.remainTime.takeIf { it > 0 }?.let { (it + 59) / 60 },
                        route?.arrivalBatteryKwh,
                    )
                }
                if (isLinking && data.activeMission != null) {
                    requestedDestination = destinationInfo
                    finishLinkRequest()
                } else if (destinationInfo.remainDistance > 0 && data.linkedMissionDestination == null) {
                    // Guidance was already running when ORBIT X connected: pick it up the same way.
                    if (data.activeMission == null) data.selectDriveMode(data.recommendedDriveMode)
                    linkCurrentDestination()
                }
            }
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            scope.launch {
                // isRouting can read false while guiding, so the guidance mode decides whether a route is up.
                val guiding = routeStateInfo.isRouting || routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING ||
                    routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING
                if (!guiding) route = null
                else arrivalKwh(routeStateInfo.remainDestinationChargingInt)?.let { energy ->
                    route = (route ?: RoutePlan("", null, null, null)).copy(arrivalBatteryKwh = energy)
                }
                if (isLinking) {
                    requestedMode = routeStateInfo.naviMode
                    finishLinkRequest()
                }
            }
        }

        override fun onRecentDestinationInfo(recentDestinationInfo: List<RecentDestinationInfo>) {
            scope.launch {
                recentPlaces = recentDestinationInfo.filter { !it.name.isNullOrBlank() }.take(4).map {
                    RecentPlace(it.name.orEmpty(), it.address.orEmpty(), it.latitude, it.longitude,
                        it.displayLatitude, it.displayLongitude, it.poiId, it.poiSubId, it.poiProvider)
                }
            }
        }

        override fun onRouteEnded() {
            scope.launch { route = null }
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            scope.launch {
                val target = data.linkedMissionDestination ?: return@launch
                val name = destinationArrivedInfo.name.orEmpty().trim()
                val address = destinationArrivedInfo.address.orEmpty().trim()
                val matches = if (target.address.isNotEmpty()) {
                    target.address == address && (target.name.isEmpty() || name.isEmpty() || target.name == name)
                } else target.name == name
                if (!matches) return@launch
                data.finishMission(null, destination = target)?.let {
                    endRequest(null)
                    postCompletion(it)
                }
            }
        }

        override fun onRouteCancelled() {
            scope.launch {
                route = null
                if (data.linkedMissionDestination != null || isLinking) {
                    clearMissionLink()
                    status = "내비 안내가 취소됐어요. 새 목적지를 연결할 수 있어요."
                }
            }
        }

        override fun onError(errorCode: NaviErrorCode) {
            scope.launch {
                if (isLinking) endRequest("Pleos 내비에서 안내 상태를 확인하지 못했어요. 길 안내를 시작한 뒤 다시 시도해 주세요.")
            }
        }
    }

    fun linkCurrentDestination() {
        if (data.activeMission == null || isLinking) return
        requestedDestination = null
        requestedMode = null
        isLinking = true
        status = "현재 Pleos 내비 목적지를 확인 중이에요."
        requestTimeout = scope.launch {
            delay(6_000)
            endRequest("내비 목적지를 확인하지 못했어요. 안내를 시작한 뒤 다시 연결해 주세요.")
        }
        val existing = helper
        if (connected && existing != null) {
            query(existing)
        } else if (existing == null) {
            connect()
        }
    }

    /** Runs [call] now if NaviHelper is connected, otherwise right after it connects. */
    private fun withClient(call: (NaviHelper) -> Unit) {
        val client = helper
        if (connected && client != null) runCatching { call(client) }
        else {
            pendingCalls += call
            if (client == null) connect()
        }
    }

    fun loadRecentPlaces() = withClient { it.getRecentDestinationInfo() }

    fun refreshRoute() = withClient {
        it.getRouteStateInfo()
        it.getDestinationInfo()
    }

    /** Starts Pleos navigation guidance to [place] directly from the app (parked only). */
    fun startRoute(place: RecentPlace) {
        if (!canOpenScreen()) return
        screenStatus = "${place.name}(으)로 Pleos 내비 안내를 요청했어요."
        withClient {
            it.requestRoute(RouteInfo(
                place.latitude, place.longitude, place.displayLatitude, place.displayLongitude,
                place.name, place.poiId.orEmpty(), place.address, place.poiSubId.orEmpty(), RouteOption.RECOMMENDED,
                place.provider.orEmpty(), false,
            ))
        }
    }

    fun openNavigation(screen: NavigationScreenRequest) {
        if (!canOpenScreen()) return
        screenTimeout?.cancel()
        pendingScreen = screen
        screenStatus = "Pleos 내비에 화면 열기를 요청하고 있어요."
        screenTimeout = scope.launch {
            delay(6_000)
            pendingScreen = null
            screenStatus = "Pleos 내비에 연결하지 못했어요. 하단 내비 버튼에서 열 수 있어요."
        }
        val client = helper
        if (connected && client != null) openPendingScreen(client)
        else if (client == null) connect()
    }

    private fun openPendingScreen(client: NaviHelper) {
        val screen = pendingScreen ?: return
        pendingScreen = null
        screenTimeout?.cancel()
        screenTimeout = null
        if (!canOpenScreen()) {
            screenStatus = "주차 상태에서 내비 화면을 열 수 있어요."
            return
        }
        screenStatus = runCatching { client.requestNavigationScreen(screen) }
            .fold(
                onSuccess = { "Pleos 내비에 요청했어요. 화면이 열리지 않으면 하단 내비 버튼을 눌러 주세요." },
                onFailure = { "내비 화면을 열지 못했어요. 하단 내비 버튼에서 열 수 있어요." },
            )
    }

    fun restoreMissionLink() {
        if (helper == null && data.linkedMissionDestination != null) connect()
    }

    private fun connect() {
        runCatching {
            val client = NaviHelper(context)
            helper = client
            connectionTimeout = scope.launch {
                delay(6_000)
                if (helper === client && !connected) connectionFailed()
            }
            client.initialize(
                onConnected = {
                    scope.launch {
                        if (helper !== client) return@launch
                        runCatching { client.addListener(listener) }
                            .onSuccess {
                                connectionTimeout?.cancel()
                                connectionTimeout = null
                                connected = true
                                if (isLinking) query(client)
                                openPendingScreen(client)
                                pendingCalls.toList().also { pendingCalls.clear() }.forEach { call -> runCatching { call(client) } }
                            }
                            .onFailure { connectionFailed() }
                    }
                },
                onFailed = { scope.launch { if (helper === client) connectionFailed() } },
            )
        }.onFailure { connectionFailed() }
    }

    private fun query(client: NaviHelper) {
        runCatching {
            client.getRouteStateInfo()
            client.getDestinationInfo()
        }
            .onFailure { endRequest("Pleos 내비 목적지를 읽을 수 없어요.") }
    }

    private fun finishLinkRequest() {
        val mode = requestedMode ?: return
        if (mode != NaviMode.ROUTE_GUIDING && mode != NaviMode.SIMULATION_GUIDING) {
            endRequest("Pleos 내비에서 목적지 안내를 시작한 뒤 연결해 주세요.")
            return
        }
        val info = requestedDestination ?: return
        val target = LinkedMissionDestination(info.name.orEmpty().trim(), info.address.orEmpty().trim(),
            simulated = mode == NaviMode.SIMULATION_GUIDING,
            latitude = info.latitude.takeIf { it != 0.0 }, longitude = info.longitude.takeIf { it != 0.0 })
        if (target.label.isBlank()) {
            endRequest("안내 중인 목적지를 확인할 수 없어요.")
        } else {
            data.linkMissionDestination(target)
            endRequest(null)
        }
    }

    private fun endRequest(message: String?) {
        requestTimeout?.cancel()
        requestTimeout = null
        isLinking = false
        requestedDestination = null
        requestedMode = null
        status = message
    }

    private fun connectionFailed() {
        if (pendingScreen != null) {
            pendingScreen = null
            screenTimeout?.cancel()
            screenTimeout = null
            screenStatus = "이 환경에서 Pleos 내비에 연결할 수 없어요."
        }
        releaseClient()
        endRequest("이 환경에서 Pleos 내비에 연결할 수 없어요.")
    }

    fun clearMissionLink() {
        data.linkMissionDestination(null)
        endRequest(null)
    }

    fun clearMissionActivity() {
        release()
        screenStatus = null
        context.getSystemService(NotificationManager::class.java)?.cancel(COMPLETION_NOTIFICATION_ID)
    }

    fun release() {
        screenTimeout?.cancel()
        screenTimeout = null
        pendingScreen = null
        endRequest(null)
        releaseClient()
    }

    private fun releaseClient() {
        connectionTimeout?.cancel()
        connectionTimeout = null
        pendingCalls.clear()
        val client = helper ?: return
        helper = null
        connected = false
        runCatching { client.removeListener(listener) }
        runCatching { client.release() }
    }

    /**
     * While guiding, if Pleos navigation predicts a low battery at the destination, offer the nearby
     * charger list as a heads-up notification over the navigation screen. The action opens the
     * native navigation charger search (no ORBIT X screen), so the driver can add a stop there.
     */
    private fun maybeSuggestCharging(plan: RoutePlan) {
        val arrival = arrivalPercent(plan)?.takeIf { it <= ChargingSuggestionPercent }
        val current = vehicleBatteryPercent?.takeIf { it <= LowBatteryPercent }
        if (arrival == null && current == null) return
        val key = plan.name.ifBlank { "route" }
        if (suggestedRoute == key) return
        suggestedRoute = key
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(SUGGESTION_CHANNEL, "주행 중 경로 제안", NotificationManager.IMPORTANCE_HIGH),
        )
        val chargers = PendingIntent.getBroadcast(
            context, 1, Intent(context, RouteSuggestionReceiver::class.java).setAction(RouteSuggestionReceiver.ACTION_CHARGERS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val later = PendingIntent.getBroadcast(
            context, 2, Intent(context, RouteSuggestionReceiver::class.java).setAction(RouteSuggestionReceiver.ACTION_LATER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val destination = plan.name.ifBlank { "목적지" }
        manager.notify(SUGGESTION_NOTIFICATION_ID, Notification.Builder(context, SUGGESTION_CHANNEL)
            .setSmallIcon(R.drawable.orbit_launcher_mark)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setContentTitle(if (arrival != null) "$destination 도착 시 배터리 약 $arrival%" else "배터리 $current% · $destination 안내 중")
            .setContentText("가는 길에 충전소를 경유지로 추가할까요?")
            .addAction(Notification.Action.Builder(null, "충전소 경유", chargers).build())
            .addAction(Notification.Action.Builder(null, "나중에", later).build())
            .setAutoCancel(true)
            .build())
        data.recordRouteSuggestion(
            if (arrival != null) "충전 경유 제안 · 도착 예상 $arrival%" else "충전 경유 제안 · 출발 배터리 $current%", null,
        )
    }

    /** Called from the notification action: opens native navigation, which works while driving. */
    fun onSuggestionAction(accepted: Boolean) {
        context.getSystemService(NotificationManager::class.java)?.cancel(SUGGESTION_NOTIFICATION_ID)
        data.recordRouteSuggestion(null, accepted)
        if (!accepted) return
        screenStatus = "주행 중 제안에서 주변 충전소를 열었어요."
        withClient { runCatching { it.requestNavigationScreen(NavigationScreenRequest.NearbyChargingStations) } }
    }

    private fun postCompletion(result: MissionCompletion) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "미션 완료", NotificationManager.IMPORTANCE_LOW))
        val open = Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val summary = if (result.award > 0) "${result.title} · +${formatPoints(result.award)}" else result.title
        manager.notify(COMPLETION_NOTIFICATION_ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.orbit_launcher_mark)
            .setContentTitle(if (result.simulated) "ORBIT X 모의 안내 도착" else "ORBIT X 도착 인증 완료")
            .setContentText(summary)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build())
    }

    companion object {
        private const val CHANNEL = "mission-completion"
        private const val COMPLETION_NOTIFICATION_ID = 1001
        private const val SUGGESTION_CHANNEL = "route-suggestion"
        private const val SUGGESTION_NOTIFICATION_ID = 1002
        private const val ChargingSuggestionPercent = 30
        private const val LowBatteryPercent = 25

        /** The live instance, so notification actions can reach NaviHelper without opening the app. */
        @Volatile var active: MissionNavigation? = null
    }

    init {
        active = this
    }
}
