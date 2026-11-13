package com.example.orbitx

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
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

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            scope.launch {
                if (isLinking && data.activeMission != null) {
                    requestedDestination = destinationInfo
                    finishLinkRequest()
                }
            }
        }

        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            scope.launch {
                if (isLinking) {
                    requestedMode = routeStateInfo.naviMode
                    finishLinkRequest()
                }
            }
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
            simulated = mode == NaviMode.SIMULATION_GUIDING)
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
        val client = helper ?: return
        helper = null
        connected = false
        runCatching { client.removeListener(listener) }
        runCatching { client.release() }
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
        manager.notify(1001, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.orbit_launcher_mark)
            .setContentTitle(if (result.simulated) "ORBIT X 모의 안내 도착" else "ORBIT X 도착 인증 완료")
            .setContentText(summary)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build())
    }

    private companion object {
        const val CHANNEL = "mission-completion"
    }
}
