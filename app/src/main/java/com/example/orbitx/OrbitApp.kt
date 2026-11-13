package com.example.orbitx

import ai.pleos.playground.navi.data.NavigationScreenRequest
import android.graphics.drawable.AnimatedImageDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.orbitx.ui.theme.LocalOrbitDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs

private val OrbitBlue: Color @Composable get() = MaterialTheme.colorScheme.primary
private val OrbitBackground: Color @Composable get() = MaterialTheme.colorScheme.background
private val OrbitPaper: Color @Composable get() = MaterialTheme.colorScheme.surface
private val OrbitInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val OrbitChrome: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val OrbitOnLight = Color(0xFF161819)
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Border: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val Glass: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val Teal: Color @Composable get() = MaterialTheme.colorScheme.tertiary
private val WidePane = 960.dp
private val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private val DimmedPreview = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(.52f, .52f, .52f, 1f) })
private const val SeparateTemperatureFeedback = "동승석 온도를 따로 맞추고 싶어요"

private enum class Page(val label: String) {
    Home("홈"), Play("PLAY"), Trip("드라이브"), Arrival("도착 기록"),
    Lab("X-LAB"), Trial("X-LAB"), Between("X-LAB"), Preference("X-LAB"), Insight("INSIGHT"), Points("MY ORBIT"),
    League("주행 효율 리그"), OtherLeagues("다른 리그 구경하기"), Ota("OTA 업데이트 평가"), LabFeedback("아이디어·피드백"),
    SyncMission("차량 기능 써보기"), ClimateLab("착석 연동 공조"),
}

@Composable
internal fun OrbitApp(
    data: OrbitData,
    parking: ParkingStatus,
    vehicle: VehicleAccess?,
    missionNavigation: MissionNavigation?,
    onLinkDestination: () -> Unit,
) {
    var page by remember { mutableStateOf(Page.Home) }
    var trial by remember { mutableStateOf<ClimateTrial?>(null) }
    val climateTask = ClimateTask.PassengerArrival
    var results by remember { mutableStateOf(emptyMap<Variant, TrialResult>()) }
    var first by remember { mutableStateOf(Variant.A) }
    var interrupted by remember { mutableStateOf(false) }
    var pointsOrigin by remember { mutableStateOf(Page.Home) }
    var otaOrigin by rememberSaveable { mutableStateOf(Page.Lab) }
    var climateOrigin by rememberSaveable { mutableStateOf(Page.Lab) }
    var openClimateComparison by rememberSaveable { mutableStateOf(false) }
    var showMissions by rememberSaveable { mutableStateOf(false) }
    var trialExitTarget by remember { mutableStateOf<Page?>(null) }
    val parked = parking == ParkingStatus.Parked
    val locked = parking == ParkingStatus.Restricted || (vehicle != null && parking == ParkingStatus.Unavailable)
    val showroom = rememberShowroomDrawable()

    fun openTab(target: Page) {
        if (target == Page.Points) pointsOrigin = Page.Home
        page = target
    }

    fun leaveTrial(target: Page = Page.ClimateLab) {
        trialExitTarget = null
        trial = null
        results = emptyMap()
        openTab(target)
    }

    fun back() {
        when (page) {
            Page.Trial, Page.Between, Page.Preference -> trialExitTarget = Page.ClimateLab
            Page.Arrival -> page = Page.Trip
            Page.Insight -> page = Page.ClimateLab
            Page.ClimateLab -> page = climateOrigin
            Page.LabFeedback -> page = Page.Lab
            Page.Ota -> page = otaOrigin
            Page.Points -> page = pointsOrigin
            Page.Trip, Page.League, Page.SyncMission -> page = Page.Play
            Page.OtherLeagues -> page = Page.League
            else -> page = Page.Home
        }
    }

    BackHandler(page != Page.Home && !locked) { back() }
    LaunchedEffect(data) {
        while (true) {
            data.refreshLeagueSeason()
            delay(30_000)
        }
    }
    LaunchedEffect(parked, page) {
        if (!parked) {
            when (page) {
                Page.Trial, Page.Between, Page.Preference -> {
                    interrupted = true
                    leaveTrial()
                }
                Page.Arrival -> page = Page.Trip
                else -> Unit
            }
        }
    }

    val exitTarget = trialExitTarget
    if (exitTarget != null && parked) {
        AlertDialog(
            onDismissRequest = { trialExitTarget = null },
            containerColor = OrbitPaper,
            title = { Text("실험을 종료할까요?") },
            text = { Text("이번 실험을 나가면 진행 중인 측정이 사라져요.") },
            confirmButton = { TextButton(onClick = { leaveTrial(exitTarget) }) { Text("실험 종료") } },
            dismissButton = { TextButton(onClick = { trialExitTarget = null }) { Text("계속하기") } },
        )
    }
    val arrival = data.pendingMissionArrival
    if (parked && arrival != null) {
        AlertDialog(
            onDismissRequest = data::acknowledgeMissionArrival,
            containerColor = OrbitPaper,
            title = { Text(if (arrival.simulated) "모의 안내 도착" else "도착 인증 완료") },
            text = {
                val destination = arrival.destination ?: "목적지"
                Text(if (arrival.award > 0) "$destination · ${arrival.title} · +${formatPoints(arrival.award)}"
                    else "$destination · ${arrival.title}")
            },
            confirmButton = {
                TextButton(onClick = {
                    data.acknowledgeMissionArrival()
                    pointsOrigin = Page.Trip
                    page = Page.Points
                }) { Text("참여 기록 보기") }
            },
            dismissButton = {
                TextButton(onClick = data::acknowledgeMissionArrival) { Text("확인") }
            },
        )
    }

    DriveLock(locked, parking) {
        val topLevel = page == Page.Home || page == Page.Play || page == Page.Lab ||
            (page == Page.Points && pointsOrigin == Page.Home)
        OrbitHeader(page, parking, topLevel, onBack = { back() })
        key(page) {
            val contentWidth = if (page == Page.Home) Modifier else Modifier.widthIn(max = 1560.dp)
            Column(
                contentWidth.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(if (page == Page.Home) 0.dp else 28.dp),
                verticalArrangement = Arrangement.spacedBy(if (page == Page.Home) 0.dp else 22.dp),
            ) {
                when (page) {
                    Page.Home -> Home(data, showroom, parked, onPlay = { page = Page.Play }, onLab = { page = Page.Lab },
                        onPoints = {
                            pointsOrigin = Page.Home
                            page = Page.Points
                        })
                    Page.Play -> Play(
                        data, showMissions, onMissionTab = { showMissions = it },
                        onTrip = { page = Page.Trip }, onLeague = { page = Page.League },
                        onSyncMission = { page = Page.SyncMission },
                        onClimateMission = {
                            climateOrigin = Page.Play
                            openClimateComparison = true
                            page = Page.ClimateLab
                        },
                        onCabinTrial = {
                            climateOrigin = Page.Play
                            openClimateComparison = false
                            page = Page.ClimateLab
                        },
                        onPoints = {
                            pointsOrigin = Page.Play
                            page = Page.Points
                        },
                        onOta = {
                            otaOrigin = Page.Play
                            page = Page.Ota
                        },
                    )
                    Page.Trip -> Trip(
                        data, vehicle, parked, missionNavigation, onLinkDestination,
                        onArrival = { page = Page.Arrival },
                    )
                    Page.Arrival -> if (parked) {
                        Arrival { feedback ->
                            if (parked && data.linkedMissionDestination == null) {
                                missionNavigation?.clearMissionLink()
                                data.finishMission(feedback)
                                pointsOrigin = Page.Trip
                                page = Page.Points
                            }
                        }
                    }
                    Page.Lab -> LabOverview(
                        data = data,
                        onClimate = {
                            climateOrigin = Page.Lab
                            openClimateComparison = false
                            page = Page.ClimateLab
                        },
                        onOta = {
                            otaOrigin = Page.Lab
                            page = Page.Ota
                        },
                        onFeedback = { page = Page.LabFeedback },
                    )
                    Page.ClimateLab -> ClimateLab(
                        data = data,
                        vehicle = vehicle,
                        parked = parked,
                        startWithComparison = openClimateComparison,
                        interrupted = interrupted,
                        hasResult = data.comparison != null,
                        availableAward = data.availableLabAward,
                        onPrevious = { page = Page.Insight },
                        onStart = {
                            if (parked) {
                                interrupted = false
                                first = data.firstVariant
                                results = emptyMap()
                                trial = ClimateTrial(first, climateTask)
                                page = Page.Trial
                            }
                        },
                    )
                    Page.Trial -> if (parked) {
                        trial?.let { current ->
                            Trial(current, results.size + 1, vehicle) { result ->
                                results = results + (current.variant to result)
                                page = if (results.size == 2) Page.Preference else Page.Between
                            }
                        }
                    }
                    Page.Between -> if (parked) {
                        val next = if (first == Variant.A) Variant.B else Variant.A
                        Intro("첫 화면 측정 완료", "같은 과제를 ${next.name}안에서 한 번 더 수행해요.")
                        Panel {
                            Eyebrow("다음 화면  ·  ${next.name}")
                            Text(next.labelFor(climateTask), style = MaterialTheme.typography.headlineMedium)
                            Action("${next.name}안 시작") {
                                trial = ClimateTrial(next, climateTask)
                                page = Page.Trial
                            }
                        }
                    }
                    Page.Preference -> if (parked) {
                        Preference(climateTask) { choice, reason ->
                            val baseline = results[Variant.A]
                            val preset = results[Variant.B]
                            if (parked && baseline != null && preset != null) {
                                data.finishExperiment(Comparison(baseline, preset, choice, reason, climateTask))
                                trial = null
                                results = emptyMap()
                                page = Page.Insight
                            }
                        }
                    }
                    Page.Insight -> data.comparison?.let {
                        Insight(it, data.lastLabAward) {
                            pointsOrigin = Page.Insight
                            page = Page.Points
                        }
                    }
                    Page.Points -> Points(data, showTitle = topLevel, canReset = !locked) {
                        if (!locked) {
                            missionNavigation?.clearMissionActivity()
                            data.resetActivityRecords()
                        }
                    }
                    Page.League, Page.OtherLeagues -> League(
                        data, vehicle, parked, page == Page.OtherLeagues,
                        onBrowse = { page = Page.OtherLeagues },
                    )
                    Page.SyncMission -> SyncMission(data, vehicle, parked, onMissions = { page = Page.Play })
                    Page.Ota -> OtaExperience(data, parked, onPoints = {
                        pointsOrigin = Page.Ota
                        page = Page.Points
                    })
                    Page.LabFeedback -> LabFeedback(data, parked, onPoints = {
                        pointsOrigin = Page.LabFeedback
                        page = Page.Points
                    })
                }
            }
        }
        val selectedTab = when (page) {
            Page.Home, Page.Play, Page.Lab, Page.Points -> page
            Page.Trip, Page.Arrival, Page.League, Page.OtherLeagues, Page.SyncMission -> Page.Play
            Page.ClimateLab, Page.Trial, Page.Between, Page.Preference, Page.Insight -> climateOrigin
            Page.LabFeedback -> Page.Lab
            Page.Ota -> otaOrigin
        }
        OrbitTabs(selectedTab) { target ->
            when (page) {
                Page.Trial, Page.Between, Page.Preference -> trialExitTarget = target
                else -> openTab(target)
            }
        }
    }
}

@Composable
private fun DriveLock(locked: Boolean, parking: ParkingStatus, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().then(
                if (locked) Modifier.blur(28.dp).clearAndSetSemantics { hideFromAccessibility() } else Modifier,
            ).background(OrbitBackground),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        if (locked) {
            val lockFocus = remember { FocusRequester() }
            val focus = LocalFocusManager.current
            LaunchedEffect(lockFocus) {
                focus.clearFocus(force = true)
                lockFocus.requestFocus()
            }
            BoxWithConstraints(
                Modifier.matchParentSize().background(Color.Black.copy(alpha = .74f))
                    .onPreviewKeyEvent { it.key != Key.Back }
                    .focusRequester(lockFocus)
                    .focusable()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val markSize = minOf(maxWidth * .34f, 214.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.orbit_launcher_mark), "ORBIT X",
                        Modifier.size(markSize), tint = Color.White,
                    )
                    if (parking == ParkingStatus.Restricted) {
                        Text("주행 중 잠금", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text("기어를 P에 놓으면 다시 사용할 수 있어요.", color = Color(0xFFDDE2E8))
                    } else {
                        Text("차량 상태 확인 중", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbitHeader(page: Page, parking: ParkingStatus, topLevel: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(OrbitChrome)
            .padding(horizontal = 24.dp, vertical = 10.dp).heightIn(min = 54.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!topLevel) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 64.dp),
                shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, Border)) {
                Text("←  뒤로", style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.orbit_launcher_mark), null, Modifier.size(36.dp), tint = OrbitInk)
            if (topLevel) Text("ORBIT X", style = MaterialTheme.typography.labelLarge)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (!topLevel) Text(page.label, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        StatusPill(parking)
    }
}

@Composable
private fun OrbitTabs(selectedTab: Page, onTab: (Page) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.fillMaxWidth().background(OrbitChrome),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(1.dp).background(Border))
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 560.dp).fillMaxWidth().selectableGroup()
                .clip(shape).background(OrbitBackground).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(Page.Home, Page.Play, Page.Lab, Page.Points).forEach { target ->
                val selected = selectedTab == target
                Box(
                    Modifier.weight(1f).heightIn(min = 64.dp).clip(shape)
                        .background(if (selected) Glass else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTab(target) })
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(target.label, style = MaterialTheme.typography.labelLarge, color = if (selected) OrbitInk else Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Home(
    data: OrbitData,
    showroom: AnimatedImageDrawable?,
    parked: Boolean,
    onPlay: () -> Unit,
    onLab: () -> Unit,
    onPoints: () -> Unit,
) {
    VehicleHero(data.availableLabAward, data.displayName, showroom, parked, onLab)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp)) {
        val showSideBySide = maxWidth >= 520.dp
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("둘러보기", style = MaterialTheme.typography.titleLarge)
            if (showSideBySide) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HomeTile("PLAY", R.drawable.orbit_home_play, Modifier.weight(1f), onPlay)
                    HomeTile("X-LAB", R.drawable.orbit_home_lab, Modifier.weight(1f), onLab)
                }
            } else {
                HomeTile("PLAY", R.drawable.orbit_home_play, Modifier.fillMaxWidth(), onPlay)
                HomeTile("X-LAB", R.drawable.orbit_home_lab, Modifier.fillMaxWidth(), onLab)
            }
            HomeTile("내 참여 기록", null, Modifier.fillMaxWidth(), onPoints, pointsLabel = "${formatPoints(data.balance)}")
        }
    }
}

@Composable
private fun HomeTile(title: String, artwork: Int?, modifier: Modifier, onClick: () -> Unit, pointsLabel: String? = null) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.height(if (artwork == null) 112.dp else 204.dp).clip(shape).background(OrbitPaper)
        .border(1.dp, Border, shape).clickable(role = Role.Button, onClick = onClick)) {
        if (artwork != null) {
            Image(
                painterResource(artwork), null, Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .86f))),
            ))
        }
        Row(
            Modifier.align(if (artwork == null) Alignment.CenterStart else Alignment.BottomStart)
                .fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = if (artwork == null) Alignment.CenterVertically else Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (pointsLabel != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.orbit_launcher_mark), null, Modifier.size(27.dp), tint = OrbitBlue)
                        Text("POINT", style = MaterialTheme.typography.labelMedium, color = OrbitBlue)
                    }
                }
                Text(title, color = if (artwork == null) OrbitInk else Color.White,
                    style = if (artwork == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
            }
            if (pointsLabel != null) Text(pointsLabel, style = MaterialTheme.typography.titleMedium)
            Text("›", fontSize = 26.sp, color = if (artwork == null) OrbitInk else Color.White)
        }
    }
}

@Composable
private fun VehicleHero(
    availableAward: Int,
    displayName: String,
    showroom: AnimatedImageDrawable?,
    parked: Boolean,
    onLab: () -> Unit,
) {
    val dark = LocalOrbitDark.current
    val shade = if (dark) Color(0xFF202224) else Color(0xFFF2F4F6)
    val backdrop = if (dark) listOf(shade, Color(0xFF46494A), Color(0xFF555859))
        else listOf(shade, Color(0xFFDDE3E9), Color(0xFFC7D0DA))
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(
            Brush.horizontalGradient(backdrop),
        ),
    ) {
        val wide = maxWidth >= WidePane
        Box(Modifier.fillMaxWidth().height(if (wide) 590.dp else 450.dp).clipToBounds()) {
            VehicleHeroImage(
                showroom,
                parked,
                if (wide) Modifier.align(Alignment.CenterEnd).fillMaxWidth(if (dark) .84f else .87f).fillMaxHeight()
                    .then(if (dark) Modifier.scale(1.04f) else Modifier)
                else Modifier.align(Alignment.TopCenter).fillMaxWidth().height(382.dp),
            )
            Box(Modifier.matchParentSize().background(
                if (wide && dark) Brush.horizontalGradient(
                    0f to shade, .24f to shade, .47f to shade.copy(alpha = .57f), .67f to Color.Transparent,
                ) else if (wide) Brush.horizontalGradient(
                    0f to shade, .20f to shade, .38f to shade.copy(alpha = .30f), .57f to Color.Transparent,
                ) else Brush.verticalGradient(
                    .27f to Color.Transparent, .53f to shade.copy(alpha = .38f), .78f to shade, 1f to shade,
                ),
            ))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to OrbitChrome, (if (dark) .19f else .12f) to Color.Transparent,
                (if (dark) .70f else .82f) to Color.Transparent, 1f to OrbitBackground,
            )))
            VehicleHeroCopy(
                if (wide) Modifier.align(Alignment.BottomStart).fillMaxWidth(.42f)
                    .padding(start = 54.dp, end = 20.dp, bottom = 76.dp)
                else Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 32.dp),
                availableAward,
                displayName,
                onLab,
            )
        }
    }
}

@Composable
private fun VehicleHeroCopy(modifier: Modifier, availableAward: Int, displayName: String, onLab: () -> Unit) {
    var hour by remember { mutableStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            hour = LocalTime.now().hour
            delay(15_000)
        }
    }
    val greeting = when (hour) {
        in 5..11 -> "좋은 아침이에요"
        in 12..17 -> "좋은 오후예요"
        in 18..21 -> "편안한 저녁 보내세요"
        else -> "좋은 밤이에요"
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (displayName.isBlank()) "PLEOS CONNECT" else "${displayName}님",
            color = Muted,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(greeting, color = OrbitInk, style = MaterialTheme.typography.displaySmall)
        Text(
            if (availableAward > 0) "첫 체험 완료 시 ${formatPoints(availableAward)} 적립"
            else "새로운 차량 기능을 직접 써보고 의견을 남겨 보세요.",
            color = Muted,
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onLab,
            modifier = Modifier.heightIn(min = 70.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface),
        ) {
            Text("X-LAB 둘러보기", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Play(
    data: OrbitData,
    showMissions: Boolean,
    onMissionTab: (Boolean) -> Unit,
    onTrip: () -> Unit,
    onLeague: () -> Unit,
    onPoints: () -> Unit,
    onOta: () -> Unit,
    onSyncMission: () -> Unit,
    onClimateMission: () -> Unit,
    onCabinTrial: () -> Unit,
) {
    Intro("PLAY", "차종별 주행 효율을 비교하고 드라이브와 차량 기능 미션에 참여해 보세요.")
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "활동", true to "미션").forEach { (missions, title) ->
            val selected = showMissions == missions
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.weight(1f).heightIn(min = 50.dp).clip(shape)
                    .background(if (selected) Glass else OrbitPaper).border(1.dp, Border, shape)
                    .selectable(selected = selected, role = Role.Tab) { onMissionTab(missions) },
                contentAlignment = Alignment.Center,
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (showMissions) {
        MissionChecklist(data, onOta, onTrip, onClimateMission, onSyncMission, onCabinTrial, onPoints)
        return
    }
    AdaptivePair(
        first = { modifier ->
            FeatureCard(
                "${data.leagueSeason.monthValue}월 시즌  ·  월 누적 $LeagueMinimumDistanceKm km",
                Page.League.label, "전기차 전비와 가솔린·하이브리드 연비를 차종별로 비교해요.",
                "내 기록과 순위 보기",
                R.drawable.orbit_league_race, modifier, onLeague,
                artworkFilter = Grayscale,
                artworkAlignment = BiasAlignment(horizontalBias = 1f, verticalBias = .6f),
                artworkScale = 1.05f,
            )
        },
        second = { modifier ->
            FeatureCard(
                if (data.activeMission == null) "목적별 드라이브" else "진행 중인 미션",
                Page.Trip.label, data.activeMission ?: "이동 목적을 고르고, 도착한 뒤 주차 상태에서 기록을 남겨요.",
                if (data.activeMission == null) "드라이브 모드 고르기" else "진행 중인 미션 보기",
                R.drawable.orbit_mission_road, modifier, onTrip, artworkFilter = Grayscale,
            )
        },
    )
    val playRecord = when {
        data.activeMission != null -> "진행 중인 미션 · ${data.activeMission}"
        data.lastMission != null -> "최근 완료 · ${data.lastMission}"
        else -> "첫 활동을 마치면 참여 기록이 여기에 표시돼요."
    }
    QuickEntry(
        R.drawable.orbit_launcher_mark, "내 참여 기록", playRecord,
        "${formatPoints(data.balance)}", Modifier.fillMaxWidth().heightIn(min = 148.dp), onPoints,
    )
}

@Composable
private fun MissionChecklist(
    data: OrbitData,
    onOta: () -> Unit,
    onTrip: () -> Unit,
    onClimateMission: () -> Unit,
    onSyncMission: () -> Unit,
    onCabinTrial: () -> Unit,
    onPoints: () -> Unit,
) {
    val otaDone = data.otaReview != null
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            today = LocalDate.now()
        }
    }
    val driveDone = data.lastDriveDay == today.toString()
    val climateDone = data.comparison != null
    val syncDone = data.syncMissionFeedback != null
    val statuses = listOf(otaDone, driveDone, climateDone, syncDone)
    val completed = statuses.count { it }
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("데일리 미션", style = MaterialTheme.typography.headlineSmall)
                Text("오늘 바로 해볼 수 있는 미션 · $completed / ${statuses.size} 완료", color = Muted)
            }
            TextButton(onClick = onPoints, modifier = Modifier.heightIn(min = 54.dp)) {
                Text("보유 ${formatPoints(data.balance)}  ›", style = MaterialTheme.typography.titleMedium)
            }
        }
        LinearProgressIndicator(progress = { completed.toFloat() / statuses.size }, modifier = Modifier.fillMaxWidth(),
            color = OrbitBlue, trackColor = Glass)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MissionCheckRow(
            "01", "도어 잠금 업데이트 평가", "주행 정보 화면에서 모든 도어를 잠그고 해제한 뒤 의견을 남겨요.",
            otaDone, "OTA 체험", data.otaReview?.award ?: data.availableOtaAward, onOta,
        )
        MissionCheckRow(
            "02", "나에게 맞는 드라이브", "추천 모드나 원하는 모드를 골라 시작하고, 도착 기록을 남겨요.",
            driveDone, if (data.activeMission == null) "모드 고르기" else "이어가기",
            if (driveDone) data.lastPlayAward else data.availableDriveAward, onTrip,
        )
        MissionCheckRow(
            "03", "동승자 탑승 공조 비교", "동승자가 탔을 때 수동 전환과 자동 전환을 써 보고 더 편한 방식을 골라요.",
            climateDone, "X-LAB 비교", if (climateDone) data.lastLabAward else data.availableLabAward, onClimateMission,
        )
        MissionCheckRow(
            "04", "앞좌석 온도 동기화 써보기", "Pleos 공조에서 SYNC를 써 보고 평소에 잘 안 썼던 이유를 남겨요.",
            syncDone, "사용법 보기", if (syncDone) data.syncMissionAward else data.availableSyncAward, onSyncMission,
        )
    }
    Panel {
        val days = data.cabinTrialDays.size
        val goal = data.cabinTrialGoal
        val feedback = data.cabinTrialFeedback
        Text("장기 미션", style = MaterialTheme.typography.headlineSmall)
        Text("착석 연동 공조 7일 써보기", style = MaterialTheme.typography.titleLarge)
        Text("주차 중 자동 제어를 켜고 착석한 구역의 공조가 1분간 확인되면 하루가 기록돼요. 사용일을 7일 모은 뒤 의견을 남겨 주세요.", color = Muted)
        Text("$days / $goal 일 · ${formatPoints(if (feedback == null) data.availableCabinTrialAward else data.cabinTrialAward)}", color = OrbitBlue)
        LinearProgressIndicator(progress = { days.toFloat() / goal }, modifier = Modifier.fillMaxWidth(),
            color = OrbitBlue, trackColor = Glass)
        when {
            feedback != null -> Text("완료 · $feedback", color = Teal)
            days >= goal -> ChoiceButtons(
                listOf("계속 사용하고 싶어요", "개선되면 사용하고 싶어요", "계속 쓸 필요는 없어요"),
                onClick = data::completeCabinTrial,
            )
            else -> Action("착석 연동 공조 열기", onClick = onCabinTrial)
        }
    }
}

@Composable
private fun MissionCheckRow(
    number: String,
    title: String,
    description: String,
    completed: Boolean,
    action: String,
    rewardPoints: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val accent = if (completed) Teal else OrbitBlue
    Row(
        Modifier.fillMaxWidth().heightIn(min = 112.dp).clip(shape).background(OrbitPaper)
            .border(1.dp, Border, shape).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(accent.copy(alpha = .12f), CircleShape),
            contentAlignment = Alignment.Center) {
            Text(if (completed) "✓" else number, color = accent, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = Muted)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (rewardPoints > 0) {
                Text(if (completed) "+${formatPoints(rewardPoints)}" else formatPoints(rewardPoints),
                    color = accent, style = MaterialTheme.typography.titleMedium)
            }
            Text(if (completed) "완료  ›" else "$action  ›", color = accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SyncMission(data: OrbitData, vehicle: VehicleAccess?, parked: Boolean, onMissions: () -> Unit) {
    var sync by remember { mutableStateOf<Boolean?>(null) }
    var changed by rememberSaveable { mutableStateOf(false) }
    var answer by rememberSaveable { mutableStateOf<String?>(null) }
    val savedAnswer = data.syncMissionFeedback
    LaunchedEffect(parked, vehicle, changed, savedAnswer) {
        if (!parked || vehicle == null || changed || savedAnswer != null) return@LaunchedEffect
        var initial: Boolean? = null
        sync = null
        while (!changed) {
            val reading = vehicle.readFrontSync()
            sync = reading
            if (reading != null) {
                if (initial == null) initial = reading
                else if (reading != initial) changed = true
            }
            delay(400)
        }
    }
    Intro("앞좌석 온도 동기화 써보기", "SYNC를 켜면 운전석에서 맞춘 온도가 동승석에도 적용돼요.")
    if (savedAnswer != null) {
        Panel {
            Eyebrow("미션 완료")
            Text("내 응답 · $savedAnswer", style = MaterialTheme.typography.titleMedium)
            Action("미션 목록으로", onClick = onMissions)
        }
        return
    }
    Panel {
        Eyebrow("순정 공조에서 사용하기")
        Text("1. Pleos 하단의 온도 숫자를 눌러 앞좌석 공조를 열어요.")
        Text("2. 현재 상태가 표시되면 순정 공조의 Sync를 한 번 눌러 상태를 바꿔 보세요.")
        Text("3. 공조 패널 바깥을 누르면 이 화면으로 돌아와요.")
        val status = when {
            changed -> "SYNC 변경 확인"
            !parked -> "주차 상태에서 확인할 수 있어요."
            sync == true -> "현재 SYNC 켜짐"
            sync == false -> "현재 SYNC 꺼짐"
            else -> "차량 공조 상태를 기다리는 중이에요."
        }
        Text(status, color = if (changed) Teal else OrbitBlue, fontWeight = FontWeight.SemiBold)
    }
    if (changed) Panel {
        Text("평소에 이 기능을 잘 안 썼다면, 이유가 뭔가요?", style = MaterialTheme.typography.titleLarge)
        listOf("사용법을 몰랐어요", "있는 줄 몰랐어요", "쓸 필요를 못 느꼈어요", "평소에도 자주 썼어요").forEach { option ->
            OutlinedButton(
                onClick = { answer = option },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (answer == option) Glass else OrbitPaper),
            ) { Text(if (answer == option) "✓  $option" else option) }
        }
        val reward = data.availableSyncAward
        Action(if (reward > 0) "응답 제출 · +${formatPoints(reward)}" else "응답 제출",
            enabled = parked && answer != null) {
            answer?.let { data.finishSyncMission(it) }
        }
    }
}

@Composable
private fun FeatureCard(
    label: String,
    title: String,
    description: String,
    action: String,
    artwork: Int,
    modifier: Modifier,
    onClick: () -> Unit,
    artworkFilter: ColorFilter? = null,
    artworkAlignment: Alignment = Alignment.Center,
    artworkScale: Float = 1f,
) {
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(
        modifier.clip(shape).background(OrbitPaper).border(1.dp, Border, shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        val dark = LocalOrbitDark.current
        val wide = maxWidth >= 500.dp
        Box(Modifier.fillMaxWidth().heightIn(min = if (wide) 460.dp else 280.dp)) {
            Image(
                painterResource(artwork), null, Modifier.matchParentSize().scale(artworkScale),
                alignment = artworkAlignment,
                contentScale = ContentScale.Crop,
                colorFilter = artworkFilter,
            )
            val cardFade = if (dark) Color(0xFF111314) else Color(0xFF263441)
            val scrim = if (!dark && wide) Brush.verticalGradient(
                .45f to Color.Transparent, .64f to cardFade.copy(alpha = .62f), .76f to cardFade,
            ) else Brush.verticalGradient(
                0f to Color.Transparent, .24f to Color.Transparent,
                (if (wide) .68f else .47f) to cardFade, 1f to cardFade,
            )
            Box(Modifier.matchParentSize().background(scrim))
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val accent = if (dark) OrbitBlue else Color(0xFFCADFF9)
                Text(label, color = accent, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.6.sp)
                Text(title, color = if (dark) OrbitInk else Color.White, style = MaterialTheme.typography.headlineMedium)
                Text(description, color = if (dark) Muted else Color(0xFFE0E7EF))
                Spacer(Modifier.height(6.dp))
                Text("$action  ›", color = accent, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun otaStatus(data: OrbitData) = if (data.otaReview != null) "평가 완료" else "체험하기"

@Composable
private fun OtaExperience(data: OrbitData, parked: Boolean, onPoints: () -> Unit) {
    var showUpdated by rememberSaveable { mutableStateOf(true) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var triedLock by rememberSaveable { mutableStateOf(false) }
    var triedUnlock by rememberSaveable { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    val review = data.otaReview

    Text("주행 정보 화면의 도어 잠금", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Panel {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to "업데이트 전", true to "업데이트 후").forEach { (updated, label) ->
                val selected = showUpdated == updated
                OutlinedButton(onClick = { showUpdated = updated }, modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Glass else OrbitPaper),
                ) { Text(label) }
            }
        }
        Panel {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("주행 정보", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Chip("앱 내 체험")
            }
            Text("0 km/h   ·   P", style = MaterialTheme.typography.headlineSmall)
            if (showUpdated) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (locked) "모든 도어 잠김" else "모든 도어 잠금 해제",
                        Modifier.weight(1f), color = if (locked) Teal else Muted,
                        style = MaterialTheme.typography.titleMedium)
                    Action(if (locked) "잠금 해제" else "모든 도어 잠그기", enabled = parked, minHeight = 56.dp) {
                        if (locked) triedUnlock = true else triedLock = true
                        locked = !locked
                    }
                }
            } else {
                Text("이 화면에는 도어 잠금 버튼이 없어요.", color = Muted)
            }
        }
    }
    if (review != null) {
        Panel {
            Eyebrow("평가 완료")
            PointActivity(review.rating, review.award)
            Text(review.feedback, color = Muted)
            Action("참여 기록 보기", minHeight = 56.dp, onClick = onPoints)
        }
    } else if (showUpdated && triedLock && triedUnlock) {
        Panel {
            Text("주행 정보 화면에서 잠금 버튼을 쓰기 쉬웠나요?", style = MaterialTheme.typography.titleLarge)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("찾기 쉽고 상태도 분명해요", "버튼은 찾았지만 잠금 상태가 헷갈려요", "버튼 위치가 눈에 잘 안 들어와요").forEach { option ->
                    val selected = feedback == option
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape)
                            .background(if (selected) Glass else OrbitBackground)
                            .border(1.dp, if (selected) OrbitBlue else Border, shape)
                            .selectable(selected = selected, enabled = parked, role = Role.RadioButton) { feedback = option }
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(option)
                    }
                }
            }
            val reward = data.availableOtaAward
            Action(if (reward > 0) "평가 제출 · +${formatPoints(reward)}" else "평가 제출",
                enabled = parked && feedback != null, minHeight = 56.dp) {
                feedback?.let { data.finishOtaReview("잠금·해제 체험", it) }
            }
        }
    } else if (showUpdated) {
        Text("잠금과 해제를 모두 써 보면 평가를 남길 수 있어요.", color = Muted)
    }
}

@Composable
private fun QuickEntry(
    icon: Int,
    title: String,
    subtitle: String,
    trailing: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.heightIn(min = 132.dp).clip(shape).background(OrbitPaper)
            .border(1.dp, Border, shape).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(30.dp), tint = OrbitInk)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Muted)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = OrbitBlue)
        Text("›", color = OrbitBlue, fontSize = 28.sp)
    }
}

private enum class DriveMode(val title: String, val description: String, val searchLabel: String) {
    Scenic("기분 전환", "전망대나 풍경이 좋은 곳으로 가요.", "전망대 찾기"),
    Together("동승자와 나들이", "함께 걷고 쉬기 좋은 수변공원으로 가요.", "수변공원 찾기"),
    Easy("편한 경로 찾기", "최근 목적지에서 익숙한 곳을 다시 골라요.", "최근 목적지 보기"),
    Charge("충전 들르기", "순정 내비에서 주변 충전소를 찾아요.", "주변 충전소 보기");

    val searchRequest: NavigationScreenRequest get() = when (this) {
        Scenic -> NavigationScreenRequest.SearchPlace("전망대")
        Together -> NavigationScreenRequest.SearchPlace("수변공원")
        Easy -> NavigationScreenRequest.RecentDestinations
        Charge -> NavigationScreenRequest.NearbyChargingStations
    }
}

@Composable
private fun Trip(
    data: OrbitData,
    vehicle: VehicleAccess?,
    parked: Boolean,
    navigation: MissionNavigation?,
    onLinkDestination: () -> Unit,
    onArrival: () -> Unit,
) {
    val activeMission = data.activeMission
    val linked = data.linkedMissionDestination
    val currentMode = DriveMode.entries.firstOrNull { it.title == activeMission }
    var changingMode by rememberSaveable { mutableStateOf(false) }
    var batteryPercent by remember { mutableStateOf<Int?>(null) }
    var occupied by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(vehicle, parked) {
        batteryPercent = null
        occupied = null
        if (vehicle != null && parked) {
            while (true) {
                batteryPercent = vehicle.readBatteryPercent()
                occupied = vehicle.readPassengerOccupied()
                delay(5_000)
            }
        }
    }
    val repeatDays = data.recentRepeatedDriveDays()
    val battery = batteryPercent
    val (recommended, reason) = when {
        battery != null && battery <= 25 -> DriveMode.Charge to "배터리 잔량이 $battery%예요."
        occupied == true -> DriveMode.Together to "동승자가 있어 함께 산책하기 좋은 곳을 골랐어요."
        repeatDays >= 3 -> DriveMode.Scenic to "최근 7일 중 ${repeatDays}일 동안 같은 곳에 도착했어요."
        else -> DriveMode.Scenic to "오늘은 풍경이 좋은 곳에 들러보세요."
    }
    Panel {
        Eyebrow("오늘의 추천")
        Text(recommended.title, style = MaterialTheme.typography.headlineMedium)
        Text(reason, color = Muted)
        if (navigation != null) {
            Action(recommended.searchLabel, enabled = parked, minHeight = 56.dp) {
                navigation.openNavigation(recommended.searchRequest)
            }
            navigation.screenStatus?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    if (activeMission == null || changingMode) {
        DriveModePicker(parked, recommended, currentMode) { mode ->
            data.selectDriveMode(mode.title)
            changingMode = false
        }
        if (activeMission != null) {
            TextButton(onClick = { changingMode = false }) { Text("변경 취소") }
        }
        if (changingMode) return
    } else {
        Panel {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Eyebrow("진행 중인 드라이브")
                    Text(activeMission, style = MaterialTheme.typography.headlineMedium)
                }
                OutlinedButton(onClick = { changingMode = true }, enabled = parked,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("모드 변경") }
            }
            linked?.let { Chip(if (it.simulated) "모의 안내 도착 대기" else "내비 도착 인증 대기") }
        }
    }
    if (activeMission != null && navigation != null) {
        Panel {
            Text("Pleos 내비 도착 인증", style = MaterialTheme.typography.titleLarge)
            if (data.availableDriveAward > 0) {
                Text("도착 기록 보상 · +${formatPoints(data.availableDriveAward)}", color = OrbitBlue)
            }
            linked?.let { Text("연결한 목적지 · ${it.label}", color = OrbitBlue) }
            Text(
                navigation.status ?: if (linked == null) {
                    "Pleos 내비에서 길 안내를 시작한 뒤 돌아와 목적지를 연결하세요."
                } else {
                    "연결한 목적지의 도착 신호를 받으면 자동으로 기록해요. 목적지를 바꾸면 다시 연결하세요."
                },
                color = Muted,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        navigation.openNavigation(currentMode?.searchRequest ?: NavigationScreenRequest.Foreground)
                    },
                    enabled = parked,
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(currentMode?.searchLabel ?: "Pleos 내비 열기") }
                Action(
                    when {
                        navigation.isLinking -> "목적지 확인 중"
                        linked != null -> "목적지 다시 연결"
                        else -> "현재 목적지 연결"
                    },
                    enabled = parked && !navigation.isLinking,
                    minHeight = 56.dp,
                    onClick = onLinkDestination,
                )
            }
        }
    }
    if (activeMission != null && linked == null) {
        OutlinedButton(onClick = onArrival, enabled = parked, modifier = Modifier.heightIn(min = 54.dp)) {
            Text("도착 시연하기")
        }
    }
    data.lastMission?.let { mission ->
        Panel {
            Text("최근 드라이브", style = MaterialTheme.typography.titleMedium)
            val destination = data.lastMissionDestination
            Text(if (destination == null) mission else "$mission · $destination", color = Muted)
            if (data.lastPlayAward > 0) Text("+${formatPoints(data.lastPlayAward)}", color = OrbitBlue)
        }
    }
}

@Composable
private fun DriveModePicker(
    parked: Boolean,
    recommended: DriveMode,
    current: DriveMode?,
    onStart: (DriveMode) -> Unit,
) {
    var selection by rememberSaveable { mutableStateOf<DriveMode?>(null) }
    val selected = selection ?: current ?: recommended
    Text("드라이브 모드", style = MaterialTheme.typography.titleLarge)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DriveMode.entries.chunked(2).forEach { modes ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                modes.forEach { mode ->
                    val shape = RoundedCornerShape(14.dp)
                    val chosen = selected == mode
                    Column(
                        Modifier.weight(1f).heightIn(min = 112.dp).clip(shape)
                            .background(if (chosen) Glass else OrbitPaper)
                            .border(1.dp, if (chosen) OrbitBlue else Border, shape)
                            .selectable(selected = chosen, role = Role.RadioButton) { selection = mode }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                    ) {
                        Text(mode.title, style = MaterialTheme.typography.titleLarge)
                        Text(mode.description, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    Action(
        if (current == null) "${selected.title} 시작" else "이 모드로 변경",
        enabled = parked && selected != current,
        minHeight = 56.dp,
    ) { if (parked) onStart(selected) }
}


private enum class LeagueSeries(
    val brand: String,
    val brandMark: Int,
    val model: String,
    val powertrain: String,
    val unit: String,
    val image: Int,
    val artFrame: IntRect,
    val accent: Color,
    val entries: List<Pair<String, Double>>,
) {
    GV90("제네시스", R.drawable.orbit_brand_genesis, "GV90", "전기", "km/kWh", R.drawable.orbit_league_gv90, IntRect(155, 56, 1632, 858), Color(0xFFD9C5A5),
        listOf("모닝크루즈" to 4.7, "윈드로드" to 4.5, "고요한길" to 4.2)),
    Ioniq5("현대", R.drawable.orbit_brand_hyundai, "아이오닉 5", "전기", "km/kWh", R.drawable.orbit_league_lineup, IntRect(15, 176, 733, 580), Color(0xFFACC9F4),
        listOf("블루웨이" to 6.4, "해안러너" to 5.9, "이브닝로드" to 5.6)),
    AvanteN("현대", R.drawable.orbit_brand_hyundai, "아반떼 N", "가솔린", "km/L", R.drawable.orbit_league_lineup, IntRect(733, 176, 1461, 580), Color(0xFFA2D5EE),
        listOf("엔라인" to 13.2, "코너메이커" to 12.6, "트랙노트" to 11.9)),
    Telluride("기아", R.drawable.orbit_brand_kia, "텔루라이드 하이브리드", "하이브리드", "km/L", R.drawable.orbit_league_lineup, IntRect(1461, 176, 2159, 580), Color(0xFFB2D5BB),
        listOf("코스트라인" to 15.3, "리버드라이브" to 14.7, "에코트랙" to 13.8)),
}

@Composable
private fun League(data: OrbitData, vehicle: VehicleAccess?, parked: Boolean, browsing: Boolean, onBrowse: () -> Unit) {
    val context = LocalContext.current
    val fuelPermission = "android.car.permission.CAR_ENERGY"
    var canReadFuel by remember {
        mutableStateOf(context.checkSelfPermission(fuelPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val fuelPermissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canReadFuel = it }
    var otherSeries by rememberSaveable { mutableStateOf<LeagueSeries?>(null) }
    var model by remember { mutableStateOf<String?>(null) }
    var showDemo by rememberSaveable { mutableStateOf(false) }
    var telemetry by remember { mutableStateOf<DrivingTelemetry?>(null) }
    var loading by remember { mutableStateOf(vehicle != null) }
    LaunchedEffect(vehicle, parked, data.leagueSeason, showDemo, browsing) {
        if (vehicle != null && parked) {
            repeat(3) {
                if (model != null) return@repeat
                model = vehicle.readLeagueModel()
                if (model == null) delay(500)
            }
        }
    }
    LaunchedEffect(vehicle, parked, data.leagueSeason, showDemo, browsing) {
        telemetry = null
        loading = vehicle != null && parked && !showDemo
        if (vehicle != null && parked && !showDemo && !browsing) {
            while (true) {
                val reading = vehicle.readDrivingTelemetry()
                telemetry = reading
                loading = false
                delay(10_000)
            }
        }
    }
    val normalizedModel = model.orEmpty().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9가-힣]"), "")
    val matchedSeries = when {
        "ioniq5" in normalizedModel && "ioniq5n" !in normalizedModel || normalizedModel == "아이오닉5" -> LeagueSeries.Ioniq5
        "gv90" in normalizedModel -> LeagueSeries.GV90
        "avanten" in normalizedModel || "elantran" in normalizedModel || normalizedModel == "아반떼n" -> LeagueSeries.AvanteN
        "telluride" in normalizedModel && ("hybrid" in normalizedModel || "hev" in normalizedModel) -> LeagueSeries.Telluride
        else -> null
    }
    val demoProfile = vehicle?.usesPleosDemoProfile == true &&
        (normalizedModel.isEmpty() || normalizedModel == "connect")
    val ownSeries = matchedSeries ?: LeagueSeries.Ioniq5.takeIf { demoProfile }
    val mySeries = if (showDemo) LeagueSeries.Ioniq5 else ownSeries
    val options = LeagueSeries.entries.filter { it != ownSeries }
    val series = if (browsing) otherSeries?.takeIf { it in options } ?: options.first() else mySeries
    val demoEfficiency = 6.0
    val isDemoSeries = series == LeagueSeries.Ioniq5
    val measuredEfficiency = when (ownSeries?.unit) {
        "km/kWh" -> data.monthlyEvEstimate?.let {
            leagueEfficiency(it.distanceKm, it.energyKwh, LeagueMinimumEnergyKwh)
        }
        "km/L" -> data.monthlyFuelEstimate?.let {
            leagueEfficiency(it.distanceKm, it.fuelLiters, LeagueMinimumFuelLiters)
        }
        else -> null
    }
    val ranking = when {
        series == null -> emptyList()
        browsing -> leaguePreviewRanking(series.entries, null, null, "")
        showDemo -> leaguePreviewRanking(
            series.entries, data.leagueDistanceKm.toFloat(), demoEfficiency, data.displayName.ifBlank { "내 시연 기록" },
        )
        else -> leaguePreviewRanking(series.entries, data.monthlyVehicleDistanceKm, measuredEfficiency, data.displayName)
    }
    val myRank = ranking.indexOfFirst { it.isMine }.takeIf { it >= 0 }?.plus(1)
    val season = data.leagueSeason
    val deadline = season.atEndOfMonth()
    if (!browsing) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(if (showDemo) "예시 기록" else "내 차종 리그", style = MaterialTheme.typography.titleMedium)
            if (!showDemo && demoProfile) Text("에뮬레이터 예시 차종 · 아이오닉 5", color = Muted)
        }
        OutlinedButton(onClick = { showDemo = !showDemo }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (showDemo) "차량 기록 보기" else "예시 기록으로 시연")
        }
    }
    if (showDemo) {
        Panel {
            if (isDemoSeries) {
                Text("${data.leagueDistanceKm} / $LeagueMinimumDistanceKm km", style = MaterialTheme.typography.headlineMedium)
                LeagueDistanceGauge(data.leagueDistanceKm.toFloat())
                if (data.hasLeagueDemo) {
                    Text("예시 순위에 반영 · ${efficiency(demoEfficiency)} km/kWh", color = OrbitBlue)
                } else {
                    Action("예시 주행 +$LeagueCourseDistanceKm km", enabled = parked, minHeight = 56.dp) {
                        if (parked) data.recordLeagueDemo()
                    }
                }
            } else {
                Text("내 기록 추가 시연은 아이오닉 5 리그에서 할 수 있어요.", color = Muted)
            }
        }
    } else if (vehicle != null) {
        LeagueVehicleRecord(data, telemetry, loading, mySeries?.unit, myRank)
        if (mySeries?.unit != "km/kWh" && !canReadFuel) {
            TextButton(onClick = { fuelPermissionRequest.launch(fuelPermission) }, enabled = parked,
                modifier = Modifier.heightIn(min = 48.dp)) { Text("연료량 읽기 허용") }
        }
    } else {
        Text("월 누적 ${LeagueMinimumDistanceKm} km 이상 주행하면 순위 등록 조건을 충족해요.", color = Muted)
    }
        OutlinedButton(onClick = onBrowse, modifier = Modifier.heightIn(min = 56.dp)) { Text("다른 리그 구경하기  ›") }
        if (series == null) {
            Text(if (model == null) "차종 신호를 확인할 수 없어요. 다른 리그에서 예시 순위를 볼 수 있어요."
                else "${model} 차종의 리그는 아직 열리지 않았어요.", color = Muted)
        }
    } else BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= WidePane) 4 else 2
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("다른 차종 · 예시 순위")
            options.chunked(columns).forEach { rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { option ->
                        val selected = series == option
                        LeagueSelector(option, selected, Modifier.weight(1f)) { otherSeries = option }
                    }
                    repeat(columns - rowOptions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (series == null) return
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OrbitPaper),
    ) {
        Box(Modifier.matchParentSize().background(
            Brush.horizontalGradient(listOf(series.accent.copy(alpha = .12f), Color.Transparent)),
        )) {
            LeagueCarImage(
                series,
                Modifier.align(Alignment.TopEnd).fillMaxWidth(.82f).fillMaxHeight(.94f)
                    .padding(top = 10.dp, end = 18.dp),
                opacity = .46f,
            )
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                0f to OrbitPaper, .23f to OrbitPaper.copy(alpha = .90f), .67f to Color.Transparent,
            )))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                .28f to Color.Transparent, .65f to OrbitPaper.copy(alpha = .92f), .86f to OrbitPaper,
            )))
        }
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Column(
                Modifier.fillMaxWidth(.70f).heightIn(min = 124.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                Text("${season.monthValue}월 시즌", color = series.readableAccent(), style = MaterialTheme.typography.labelLarge)
                Text("${series.model} 리그", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                val metric = if (series.unit == "km/kWh") "전비" else "연비"
                Text("${series.powertrain}  ·  $metric ${series.unit}", color = series.readableAccent())
                Text("마감  ${deadline.year}년 ${deadline.monthValue}월 ${deadline.dayOfMonth}일", color = Muted)
            }
            Spacer(Modifier.height(6.dp))
            Eyebrow(if (!showDemo && myRank != null) "내 차량 기록 + 예시 참가자 · 시상 예시"
                else "시연용 순위 · 시즌 시상 예시")
            LeaguePodium(ranking, series)
            ranking.drop(3).forEachIndexed { index, (name, value, isMine) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("${index + 4}위", color = Muted)
                    Text(if (isMine) "${name} · 내 기록" else name, Modifier.weight(1f),
                        color = if (isMine) OrbitBlue else OrbitInk)
                    Text("${efficiency(value)} ${series.unit}")
                }
            }
        }
    }
}

@Composable
private fun LeagueVehicleRecord(data: OrbitData, telemetry: DrivingTelemetry?, loading: Boolean, unit: String?, myRank: Int?) {
    val distance = data.monthlyVehicleDistanceKm
    val ev = data.monthlyEvEstimate
    val fuel = data.monthlyFuelEstimate
    Panel {
        Text("이달 내 주행 기록", style = MaterialTheme.typography.titleLarge)
        if (distance != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(String.format(Locale.KOREA, "%.1f", distance), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Text("km", Modifier.padding(bottom = 3.dp), color = Muted,
                    style = MaterialTheme.typography.titleMedium)
            }
            LeagueDistanceGauge(distance)
            val rankStatus = when {
                myRank != null -> "내 기록 · 예시 순위 ${myRank}위"
                distance < LeagueMinimumDistanceKm -> "순위 기준까지 ${efficiency((LeagueMinimumDistanceKm - distance).toDouble())} km"
                unit == null -> "500 km 충족 · 내 차종 리그 확인 필요"
                else -> "500 km 충족 · 전비·연비 측정값 대기 중"
            }
            Text(rankStatus, color = if (myRank != null) OrbitBlue else Muted)
        } else {
            Text(if (loading) "차량에서 주행거리를 읽고 있어요." else "이달 주행거리를 확인할 수 없어요.", color = Muted)
            Text("순위 등록 기준 · 월 누적 $LeagueMinimumDistanceKm km", color = OrbitBlue)
        }
        val details = listOfNotNull(
            telemetry?.odometerKm?.let { String.format(Locale.KOREA, "계기판 %.1f km", it) },
            telemetry?.fuelPercent?.let { "연료 잔량 $it%" },
        )
        if (distance != null || details.isNotEmpty()) {
            Text(
                (listOfNotNull("연결 이후 집계".takeIf { distance != null }) + details).joinToString("  ·  "),
                color = Muted, style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (unit != "km/L" && (unit != null || ev != null || telemetry?.evEfficiency != null)) {
            MonthlyEfficiency("전비", "km/kWh", ev?.distanceKm, ev?.energyKwh, LeagueMinimumEnergyKwh)
        }
        if (unit != "km/kWh" && (unit != null || fuel != null || telemetry?.fuelEconomy != null)) {
            MonthlyEfficiency("연비", "km/L", fuel?.distanceKm, fuel?.fuelLiters, LeagueMinimumFuelLiters)
        }
        if (unit == null && ev == null && fuel == null && telemetry?.evEfficiency == null && telemetry?.fuelEconomy == null) {
            Text("이달 전비·연비 · 차량 측정 신호 대기 중", color = Muted)
        }
        val readings = listOfNotNull(
            telemetry?.evEfficiency?.let { "${it.period} 전비 ${efficiency(it.value.toDouble())} km/kWh" },
            telemetry?.fuelEconomy?.let { "${it.period} 연비 ${efficiency(it.value.toDouble())} km/L" },
        )
        if (readings.isNotEmpty()) Text(
            "차량 제공 참고값 · ${readings.joinToString("  ·  ")}",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MonthlyEfficiency(label: String, unit: String, distance: Float?, used: Float?, minimum: Float) {
    val value = leagueEfficiency(distance, used, minimum)?.let { "${efficiency(it)} $unit" }
        ?: "측정 데이터 수집 중"
    Text("이달 추정 $label · $value", color = OrbitBlue, style = MaterialTheme.typography.titleMedium)
    distance?.let { Text("효율 계산에 포함된 거리 ${efficiency(it.toDouble())} km", color = Muted) }
}

@Composable
private fun LeagueDistanceGauge(distanceKm: Float) {
    val intervals = 2
    val labelWidth = 42.dp
    val progress = (distanceKm / LeagueMinimumDistanceKm.toFloat()).coerceIn(0f, 1f)
    val progressColor = OrbitBlue
    val labelColor = Muted
    val markerColor = OrbitInk
    val trackColor = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = labelWidth / 2)) {
            val trackY = 17.dp.toPx()
            val currentX = size.width * progress
            val stroke = 4.dp.toPx()
            val start = Offset(0f, trackY)
            drawLine(trackColor, start, Offset(size.width, trackY), stroke, StrokeCap.Round)
            if (progress > 0f) drawLine(progressColor, start, Offset(currentX, trackY), stroke, StrokeCap.Round)
            for (index in 0..intervals) {
                val x = size.width * index / intervals
                drawLine(labelColor, Offset(x, trackY + 5.dp.toPx()), Offset(x, trackY + 8.dp.toPx()), 1.dp.toPx())
            }
            val halfWidth = 6.dp.toPx()
            val marker = Path().apply {
                moveTo(currentX - halfWidth, 2.dp.toPx())
                lineTo(currentX + halfWidth, 2.dp.toPx())
                lineTo(currentX, 11.dp.toPx())
                close()
            }
            drawPath(marker, markerColor)
            drawLine(markerColor, Offset(currentX, 11.dp.toPx()), Offset(currentX, trackY), 1.5.dp.toPx())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (index in 0..intervals) {
                Text(
                    (LeagueMinimumDistanceKm * index / intervals).toString(),
                    Modifier.width(labelWidth),
                    color = Muted,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LeagueSeries.readableAccent(): Color =
    if (LocalOrbitDark.current) accent else lerp(accent, OrbitInk, .76f)

@Composable
private fun LeagueSelector(series: LeagueSeries, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.heightIn(min = 104.dp).clip(shape)
            .background(if (selected) series.accent.copy(alpha = .12f) else OrbitPaper)
            .border(1.dp, if (selected) series.readableAccent() else Border, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        Icon(
            painterResource(series.brandMark), contentDescription = null,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(.34f).height(52.dp).padding(end = 14.dp),
            tint = OrbitInk.copy(alpha = if (selected) .96f else .76f),
        )
        Column(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(.64f).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${series.brand} · ${series.powertrain}", color = series.readableAccent(), style = MaterialTheme.typography.labelSmall)
            Text(series.model.removeSuffix(" 하이브리드"), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LeagueCarImage(series: LeagueSeries, modifier: Modifier, opacity: Float) {
    val bitmap = ImageBitmap.imageResource(series.image)
    val frame = series.artFrame
    val painter = remember(bitmap, frame) {
        BitmapPainter(bitmap, srcOffset = IntOffset(frame.left, frame.top), srcSize = frame.size,
            filterQuality = FilterQuality.High)
    }
    val dark = LocalOrbitDark.current
    val ink = OrbitInk
    val colorFilter = remember(series, dark, ink) {
        val tint = lerp(if (dark) Color(0xFFD7DEE4) else ink, series.accent, if (dark) .34f else .14f)
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, tint.red * 255f,
            0f, 0f, 0f, 0f, tint.green * 255f,
            0f, 0f, 0f, 0f, tint.blue * 255f,
            .23f, .77f, .08f, 0f, -18f,
        )))
    }
    Image(painter, null, modifier, alignment = Alignment.CenterEnd, contentScale = ContentScale.Fit,
        alpha = opacity, colorFilter = colorFilter)
}

@Composable
private fun LeaguePodium(ranking: List<LeagueEntry>, series: LeagueSeries) {
    val prizes = listOf("50,000 P", "20,000 P", "10,000 P")
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(2 to 62.dp, 1 to 96.dp, 3 to 50.dp).forEach { (rank, height) ->
            val (name, value, isMine) = ranking[rank - 1]
            val step = if (rank == 1) series.accent else Color(0xFFB2BBC6)
            Column(
                Modifier.weight(if (rank == 1) 1.17f else 1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LeagueMedal(rank)
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium, color = if (isMine) OrbitBlue else OrbitInk)
                Text("${efficiency(value)} ${series.unit}", color = Muted, style = MaterialTheme.typography.labelMedium)
                Text(prizes[rank - 1], color = OrbitBlue,
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.fillMaxWidth().padding(top = 4.dp).height(height)
                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                        .background(Brush.verticalGradient(listOf(step, lerp(step, OrbitOnLight, .16f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(rank.toString(), color = OrbitOnLight, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LeagueMedal(rank: Int) {
    val medalOutline = OrbitOnLight.copy(alpha = .38f)
    val metal = when (rank) {
        1 -> Color(0xFFF1CD72)
        2 -> Color(0xFFCBD5E3)
        else -> Color(0xFFD99D79)
    }
    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val unit = size.width
            val ribbon = Path().apply {
                moveTo(unit * .19f, 0f)
                lineTo(unit * .39f, 0f)
                lineTo(unit * .50f, unit * .21f)
                lineTo(unit * .61f, 0f)
                lineTo(unit * .81f, 0f)
                lineTo(unit * .64f, unit * .43f)
                lineTo(unit * .36f, unit * .43f)
                close()
            }
            drawPath(ribbon, metal.copy(alpha = .68f))
            val center = Offset(unit * .5f, unit * .62f)
            drawCircle(metal, unit * .35f, center)
            drawCircle(medalOutline, unit * .28f, center, style = Stroke(width = 1.dp.toPx()))
        }
        Text("${rank}위", Modifier.padding(top = 9.dp), color = OrbitOnLight, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold)
    }
}

private fun efficiency(value: Double) = String.format(Locale.KOREA, "%.1f", value)

@Composable
private fun Arrival(onFinish: (String?) -> Unit) {
    Intro("어떤 경험이었나요?", "도착 후 느낀 점을 하나 남기면 다음 드라이브에 참고할 수 있어요.")
    Panel {
        Eyebrow("도착 후 피드백")
        ChoiceButtons(listOf("경로가 편했어요", "경로 안내가 복잡했어요", "동승자 배려가 필요했어요")) { onFinish(it) }
        OutlinedButton(onClick = { onFinish(null) }) { Text("응답 없이 기록 완료") }
    }
}

@Composable
private fun LabOverview(data: OrbitData, onClimate: () -> Unit, onOta: () -> Unit, onFeedback: () -> Unit) {
    Intro("X-LAB", "고객의 제안과 새로운 아이디어를 직접 써보고, 다음 변화를 함께 만들어요.")
    AdaptivePair(
        first = { modifier ->
            FeatureCard(
                "실험 01  ·  차량 기능", Page.ClimateLab.label,
                "앞뒤 네 좌석의 착석 신호에 맞춰 공조가 어떻게 바뀌는지 체험해요.",
                "공조 실험 보기", R.drawable.orbit_climate_preview, modifier, onClimate,
                artworkFilter = if (LocalOrbitDark.current) DimmedPreview else null,
                artworkAlignment = Alignment.CenterStart,
            )
        },
        second = { modifier ->
            FeatureCard(
                "실험 02  ·  소프트웨어", Page.Ota.label,
                "주행 정보 화면에 추가된 도어 잠금 버튼을 써 보고 평가해요.",
                otaStatus(data), R.drawable.orbit_lab_art, modifier, onOta, artworkFilter = Grayscale,
            )
        },
    )
    QuickEntry(
        R.drawable.orbit_lab, "아이디어·피드백", "필요한 차량 기능을 제안하거나 체험 후 의견을 남겨요.",
        "의견 남기기", Modifier.fillMaxWidth().heightIn(min = 148.dp), onFeedback,
    )
}

@Composable
private fun ClimateLab(
    data: OrbitData,
    vehicle: VehicleAccess?,
    parked: Boolean,
    startWithComparison: Boolean,
    interrupted: Boolean,
    hasResult: Boolean,
    availableAward: Int,
    onPrevious: () -> Unit,
    onStart: () -> Unit,
) {
    var cabin by remember { mutableStateOf<CabinClimateSnapshot?>(null) }
    var comparison by rememberSaveable { mutableStateOf(startWithComparison) }
    val automatic = data.automaticCabinClimate
    LaunchedEffect(parked, vehicle, comparison, automatic) {
        if (parked && vehicle != null && !comparison) {
            vehicle.observeCabinClimate(automatic) { cabin = it }
        } else {
            cabin = null
        }
    }
    val qualifying = parked && !comparison && automatic && cabin?.occupiedZonePowered == true
    LaunchedEffect(qualifying) {
        while (qualifying && data.cabinTrialDays.size < data.cabinTrialGoal) {
            delay(60_000)
            data.recordCabinTrialDay()
        }
    }
    Intro("탑승에 따라 바뀌는 공조", "X-LAB · 출시 전 체험")
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("자동 공조" to false, "개선안 비교" to true).forEach { (label, selected) ->
            OutlinedButton(onClick = { comparison = selected },
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (comparison == selected) Glass else OrbitPaper,
                    contentColor = OrbitInk)) { Text(label, fontWeight = FontWeight.SemiBold) }
        }
    }
    if (!comparison) {
        Panel {
            val snapshot = cabin
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("좌석별 착석 상태", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                Chip(when (snapshot?.coolingOn) {
                    true -> "A/C ON"
                    false -> "A/C OFF"
                    null -> "A/C 확인 중"
                })
            }
            Text(
                if (!automatic) "자동 제어 꺼짐 · 차량 상태만 표시해요."
                else snapshot?.status ?: "주차 상태와 차량 연결을 확인하고 있어요.",
                color = Muted,
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 54.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("착석 연동 공조", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (automatic) "켜짐 · 주차 중 착석에 맞춰 조절해요." else "꺼짐 · 순정 공조에서 직접 조절할 수 있어요.",
                        color = Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = automatic,
                    onCheckedChange = {
                        cabin = cabin?.copy(occupiedZonePowered = false)
                        data.saveAutomaticCabinClimate(it)
                    },
                    enabled = automatic || (parked && vehicle != null),
                    modifier = Modifier.semantics { contentDescription = "착석 연동 공조" },
                )
            }
            if (automatic) Text("목표 실내온도 ${CabinTargetCelsius.toInt()}°C", color = Muted,
                style = MaterialTheme.typography.bodyMedium)
            CabinSeatMap(snapshot)
            if (snapshot?.zones?.any { it.seats.size > 1 } == true) {
                Text("같은 공조 구역을 공유하는 좌석은 전원이 함께 켜져요.", color = Muted)
            }
        }
    }
    if (comparison) {
      Panel {
        Text("혼자 탈 때는 운전석만, 동승자가 타면 앞좌석으로", style = MaterialTheme.typography.titleLarge)
        Text(
            if (availableAward > 0) "첫 완료 보상  +${formatPoints(availableAward)}" else "수동 전환과 자동 전환을 체험하고 선호를 남겨요.",
            color = OrbitBlue,
            style = MaterialTheme.typography.labelLarge,
        )
        AdaptivePair(
            first = { modifier -> LabPreview(Variant.A, ClimateTask.PassengerArrival, modifier) },
            second = { modifier -> LabPreview(Variant.B, ClimateTask.PassengerArrival, modifier) },
        )
        Text(
            "Pleos 착석 신호 또는 화면 버튼으로 탑승과 하차를 체험해요. 송풍 범위는 앱에서 재현해요.",
            color = Muted,
        )
        if (interrupted) Text("주차 상태가 바뀌어 진행 중이던 실험이 종료됐어요.", color = OrbitBlue)
        if (!parked) Text("기어가 P이고 Pleos 주행 제한이 해제되면 시작할 수 있어요.", color = Muted)
        Action("A/B 비교 시작", enabled = parked, onClick = onStart)
        if (hasResult) OutlinedButton(onClick = onPrevious, modifier = Modifier.heightIn(min = 54.dp)) {
            Text("최근 실험 결과")
        }
      }
    }
}

@Composable
private fun CabinSeatMap(snapshot: CabinClimateSnapshot?) {
    val cabin = ImageBitmap.imageResource(R.drawable.orbit_cabin_top_view)
    val hoodCrop = (cabin.height * .20f).toInt()
    val visibleHeight = cabin.height - hoodCrop
    val artwork = remember(cabin) {
        BitmapPainter(cabin, srcOffset = IntOffset(0, hoodCrop),
            srcSize = IntSize(cabin.width, visibleHeight), filterQuality = FilterQuality.High)
    }
    val ratio = cabin.width.toFloat() / visibleHeight
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.widthIn(max = 860.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Glass, OrbitBackground))),
        ) {
            val mapHeight = (maxWidth * .88f).coerceAtMost(570.dp)
            val mapWidth = mapHeight * ratio
            val labelWidth = ((maxWidth - mapWidth) / 2 - 10.dp).coerceIn(80.dp, 164.dp)
            val gap = (maxWidth - mapWidth) / 2 - labelWidth
            Box(Modifier.fillMaxWidth().height(mapHeight)) {
                Box(Modifier.align(Alignment.Center).width(mapWidth).fillMaxHeight()) {
                    Image(artwork, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                    CabinSeat.entries.forEach { seat ->
                        val occupied = snapshot?.occupied?.get(seat) == true
                        val eco = snapshot?.ecoSeats?.contains(seat.value) == true
                        if (occupied || eco) {
                            val left = seat == CabinSeat.Driver || seat == CabinSeat.RearLeft
                            val front = seat == CabinSeat.Driver || seat == CabinSeat.Passenger
                            val x = mapWidth * if (left) .34f else .63f
                            val rowCenter = if (front) .50f else .70f
                            val y = mapHeight * (rowCenter * cabin.height - hoodCrop) / visibleHeight
                            val size = (mapWidth * .17f).coerceIn(28.dp, 50.dp)
                            val marker = Modifier.offset(x - size / 2, y - size / 2).size(size)
                            if (occupied) CabinOccupant(marker)
                            else Box(marker.background(Teal.copy(alpha = .2f), CircleShape).border(1.dp, Teal, CircleShape))
                        }
                    }
                }
                CabinSeat.entries.forEach { seat ->
                    val left = seat == CabinSeat.Driver || seat == CabinSeat.RearLeft
                    val front = seat == CabinSeat.Driver || seat == CabinSeat.Passenger
                    CabinSeatLabel(
                        seat, snapshot,
                        Modifier.align(BiasAlignment(if (left) -1f else 1f, if (front) -.28f else .31f))
                            .offset(x = if (left) gap / 2 else -gap / 2).width(labelWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun CabinSeatLabel(seat: CabinSeat, snapshot: CabinClimateSnapshot?, modifier: Modifier) {
    val occupied = snapshot?.occupied?.get(seat)
    val zone = snapshot?.zones?.firstOrNull { seat.value in it.seats }
    val fan = snapshot?.fanSpeeds?.firstOrNull { seat.value in it.seats }
    val temperature = snapshot?.temperatures?.firstOrNull { seat.value in it.seats }?.value
    val measured = snapshot?.measuredTemperatures?.firstOrNull { seat.value in it.seats }?.value
    val eco = snapshot?.ecoSeats?.contains(seat.value) == true
    val accent = if (occupied == true) OrbitBlue else if (eco) Teal else Muted
    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier.clip(shape).background(OrbitBackground.copy(alpha = .95f))
            .border(1.dp, if (occupied == true || eco) accent.copy(alpha = .7f) else Border, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(seat.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            when {
                occupied == true -> "착석"
                eco -> "절전 적용"
                occupied == false -> "빈 좌석"
                else -> "확인 중"
            },
            color = accent,
            style = MaterialTheme.typography.labelMedium,
        )
        val details = listOfNotNull(
            temperature?.takeIf { zone?.powered != false }?.let { degrees(it) },
            fan?.value?.takeIf { zone?.powered != false }?.let { "${it}단" },
        )
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), color = OrbitInk, style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center)
        }
        measured?.let { Text("실내 ${degrees(it)}", color = Muted, style = MaterialTheme.typography.labelSmall) }
        snapshot?.seatComfort?.get(seat)?.let {
            val mode = if (it.heating) "열선" else "통풍"
            val heatColor = if (LocalOrbitDark.current) Color(0xFFF0BB9D) else Color(0xFF8D3D1F)
            Text("$mode ${it.level}단", color = if (it.heating) heatColor else OrbitBlue,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CabinOccupant(modifier: Modifier) {
    val accent = if (LocalOrbitDark.current) OrbitBlue else Color(0xFFD9E8FC)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val body = Color(0xFFA8BEDB)
        drawCircle(Color(0xE0253A50), w * .50f)
        drawCircle(accent.copy(alpha = .65f), w * .48f, style = Stroke(width = 1.dp.toPx()))
        drawRoundRect(body, Offset(w * .25f, h * .42f), Size(w * .50f, h * .28f), CornerRadius(w * .14f))
        drawRoundRect(body, Offset(w * .30f, h * .62f), Size(w * .15f, h * .21f), CornerRadius(w * .07f))
        drawRoundRect(body, Offset(w * .55f, h * .62f), Size(w * .15f, h * .21f), CornerRadius(w * .07f))
        drawCircle(accent, w * .135f, Offset(w * .50f, h * .30f))
    }
}

@Composable
private fun LabFeedback(data: OrbitData, parked: Boolean, onPoints: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(LabSubmissionType.Idea) }
    var message by rememberSaveable { mutableStateOf("") }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val reward = data.availableSubmissionAward
    LaunchedEffect(parked) {
        if (!parked) focus.clearFocus()
    }
    Intro("아이디어·피드백", "차에서 쓰고 싶은 기능이나 체험에서 느낀 점을 들려주세요.")
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabSubmissionType.entries.forEach { option ->
                val selected = option == type
                OutlinedButton(
                    onClick = { type = option; notice = null },
                    enabled = parked,
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Glass else OrbitPaper),
                ) { Text(option.title) }
            }
        }
        Text(type.prompt, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(240); notice = null },
            modifier = Modifier.fillMaxWidth(),
            enabled = parked,
            label = { Text("내용") },
            minLines = 3,
            maxLines = 5,
            supportingText = { Text("${message.length}/240자 · 최소 5자") },
        )
        Text(
            if (reward > 0) "오늘 첫 의견을 남기면 +${formatPoints(reward)}를 받아요."
            else "의견은 계속 남길 수 있고, 포인트는 하루 첫 제출에만 적립돼요.",
            color = OrbitBlue,
        )
        Text("제안과 의견은 시연용으로 이 기기에 저장돼요.", color = Muted)
        if (!parked) Text("주차 후 의견을 남길 수 있어요.", color = Muted)
        Action("의견 남기기", enabled = parked && message.trim().length >= 5) {
            if (parked) {
                val submitted = data.submitLabFeedback(type, message)
                notice = when {
                    submitted == null -> "같은 내용이 이미 저장됐어요."
                    submitted.award > 0 -> "의견을 저장했어요. +${formatPoints(submitted.award)} 적립"
                    else -> "의견을 저장했어요."
                }
                if (submitted != null) {
                    message = ""
                    focus.clearFocus()
                }
            }
        }
        notice?.let { Text(it, color = Teal) }
    }
    if (data.labSubmissions.isNotEmpty()) {
        Panel {
            Text("내 제안과 의견", style = MaterialTheme.typography.titleLarge)
            data.labSubmissions.forEach { submission ->
                PointActivity("${submission.type.title} · ${submission.date}", submission.award)
                Text(submission.message, color = Muted)
            }
            Action("참여 기록 보기", onClick = onPoints)
        }
    }
}

@Composable
private fun LabPreview(variant: Variant, task: ClimateTask, modifier: Modifier) {
    val steps = when (task) {
        ClimateTask.PassengerArrival -> listOf("동승석 탑승", "앞좌석 송풍으로 변경", "하차 후 운전석 송풍으로 변경")
        ClimateTask.SyncPreset -> listOf("공조 열기", "양쪽 온도 조절", "Auto와 SYNC 켜기")
        ClimateTask.SeatHold -> listOf("공조 열기", "SYNC 끄기", "운전석 온도 조절")
    }
    Column(
        modifier.heightIn(min = 222.dp).clip(RoundedCornerShape(16.dp))
            .background(if (variant == Variant.A) OrbitBackground else MaterialTheme.colorScheme.primaryContainer)
            .border(1.dp, if (variant == Variant.A) Border else OrbitBlue.copy(alpha = .3f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Eyebrow(if (variant == Variant.A) "A  ·  기존 방식" else "B  ·  개선 제안")
        Text(variant.labelFor(task), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (variant == Variant.A) {
            steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Glass), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = OrbitBlue, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(step, color = Muted, style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            Text(task.presetLabel, color = OrbitInk, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (task == ClimateTask.PassengerArrival) "탑승하면 앞좌석으로, 하차하면 운전석으로 자동 전환해요."
                else "한 번 눌러 원하는 설정을 적용해요.",
                color = Muted, style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun degrees(value: Float?) =
    if (value == null) "조회 불가" else String.format(Locale.KOREA, "%.1f°C", value)

private val ClimateTask.instructions get() = when (this) {
    ClimateTask.PassengerArrival -> "동승자가 타고 내릴 때 송풍 범위가 어떻게 바뀌는지 확인해요."
    ClimateTask.SyncPreset -> "양쪽을 23°C로 맞추고 Auto와 SYNC를 켜 보세요."
    ClimateTask.SeatHold -> "동승석은 24°C로 두고, 운전석만 23°C로 맞춰 보세요."
}

private val ClimateTask.presetLabel get() = when (this) {
    ClimateTask.PassengerArrival -> "운전석 ↔ 앞좌석"
    ClimateTask.SyncPreset -> "23°C · Auto · SYNC"
    ClimateTask.SeatHold -> "운전석만 23°C"
}

private fun Variant.labelFor(task: ClimateTask) = when {
    task != ClimateTask.PassengerArrival -> title
    this == Variant.A -> "기본 조작 재현"
    else -> "동승석 자동 전환"
}

@Composable
private fun AdaptivePair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    minimumPairWidth: Dp = WidePane,
    matchHeight: Boolean = false,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= minimumPairWidth) {
            Row(
                modifier = if (matchHeight) Modifier.height(IntrinsicSize.Min) else Modifier,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val panel = Modifier.weight(1f).then(if (matchHeight) Modifier.fillMaxHeight() else Modifier)
                first(panel)
                second(panel)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Trial(trial: ClimateTrial, round: Int, vehicle: VehicleAccess?, onResult: (TrialResult) -> Unit) {
    Intro(trial.task.title, trial.task.instructions)
    Panel {
        Eyebrow("화면 ${trial.variant.name}  ·  ${trial.variant.labelFor(trial.task)}     ${round}/2")
        if (trial.task == ClimateTask.PassengerArrival) {
            PassengerClimateTrial(trial.variant, vehicle, onResult)
        } else if (trial.variant == Variant.A) {
            if (trial.panelOpen) {
                BaselineClimatePanel(trial, onResult)
            } else {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF171E2A)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val openLibrary = !trial.task.goalSync && !trial.libraryOpen
                    Text(if (trial.libraryOpen) "Pleos 앱 라이브러리" else "Pleos 하단 바", color = Color(0xFFC9D5E8))
                    Button(
                        onClick = if (openLibrary) trial::openLibrary else trial::openPanel,
                        modifier = Modifier.heightIn(min = 56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF35465F)),
                    ) {
                        val action = when {
                            openLibrary -> "앱 라이브러리 열기"
                            trial.libraryOpen -> "공조 탭 열기"
                            else -> "운전석 ${degrees(trial.driverTemperature)} · 공조 열기"
                        }
                        Text(action, color = Color.White)
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF405D85)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("원터치 컴포트", color = Color.White,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (trial.task.goalSync) "앞좌석 양쪽 23°C · Auto · SYNC"
                    else "운전석 23°C · 동승석 24°C 유지 · SYNC 끄기",
                    color = Color(0xFFEDF5FF),
                )
                Button(
                    onClick = { trial.applyPreset()?.let(onResult) },
                    modifier = Modifier.heightIn(min = 58.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDDEBFF), contentColor = OrbitOnLight),
                ) {
                    Text(if (trial.task.goalSync) "컴포트 적용" else trial.task.presetLabel,
                        fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun PassengerClimateTrial(variant: Variant, vehicle: VehicleAccess?, onResult: (TrialResult) -> Unit) {
    var occupied by remember { mutableStateOf<Boolean?>(null) }
    var source by remember { mutableStateOf<ClimateSignal?>(null) }
    var phase by remember { mutableStateOf(PassengerPhase.AwaitArrival) }
    val automatic = variant == Variant.B
    val frontAirflow = phase == PassengerPhase.AwaitDeparture || phase == PassengerPhase.SelectDriver

    fun observeSeat(value: Boolean, signal: ClimateSignal) {
        if (source != null && source != signal) return
        when {
            phase == PassengerPhase.AwaitArrival && value -> {
                source = signal
                phase = if (automatic) PassengerPhase.AwaitDeparture else PassengerPhase.SelectFront
            }
            phase == PassengerPhase.SelectFront && !value -> {
                source = null
                phase = PassengerPhase.AwaitArrival
            }
            phase == PassengerPhase.AwaitDeparture && !value -> {
                phase = if (automatic) PassengerPhase.Complete else PassengerPhase.SelectDriver
            }
        }
    }

    LaunchedEffect(vehicle) {
        if (vehicle == null) return@LaunchedEffect
        var previous: Boolean? = null
        var sawEmptySeat = false
        while (phase != PassengerPhase.Complete && (source == null || source == ClimateSignal.Pleos)) {
            val current = vehicle.readPassengerOccupied()
            occupied = current
            if (current != null && current == previous) {
                if (!current) sawEmptySeat = true
                if (sawEmptySeat) observeSeat(current, ClimateSignal.Pleos)
            }
            previous = current
            delay(450)
        }
    }

    Eyebrow("앱 내 송풍 체험")
    Text(if (frontAirflow) "앞좌석 전체 송풍" else "운전석 전용 송풍",
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    when (phase) {
        PassengerPhase.AwaitArrival -> {
            Text(when (occupied) {
                false -> "동승석이 비어 있어요. Pleos에서 착석 상태를 바꾸거나 아래 버튼을 눌러요."
                true -> "동승석이 이미 차 있어요. Pleos에서 동승석을 비웠다가 다시 채워요."
                null -> "화면 버튼으로 동승자 탑승을 체험할 수 있어요."
            }, color = Muted)
            Action("탑승 시연") { observeSeat(true, ClimateSignal.Simulated) }
        }
        PassengerPhase.SelectFront -> {
            Text("동승자가 탔어요. 송풍 범위를 앞좌석 전체로 바꿔요.", color = Muted)
            Action("앞좌석 전체로 변경") { phase = PassengerPhase.AwaitDeparture }
        }
        PassengerPhase.AwaitDeparture -> {
            Text(if (automatic) "탑승을 감지해 앞좌석 전체로 전환했어요." else "앞좌석 전체로 바꿨어요.",
                color = Muted)
            if (source == ClimateSignal.Pleos) {
                Text("Pleos에서 동승석을 비우면 하차를 확인해요.", color = OrbitBlue)
                OutlinedButton(onClick = {
                    source = ClimateSignal.Mixed
                    observeSeat(false, ClimateSignal.Mixed)
                }) { Text("하차 시연으로 계속") }
            } else {
                Action("하차 시연") { observeSeat(false, ClimateSignal.Simulated) }
            }
        }
        PassengerPhase.SelectDriver -> {
            Text("동승자가 내렸어요. 운전석 전용으로 바꿔요.", color = Muted)
            Action("운전석 전용으로 변경") { phase = PassengerPhase.Complete }
        }
        PassengerPhase.Complete -> {
            Text(if (automatic) "하차를 감지해 운전석 전용으로 돌아왔어요." else "운전석 전용으로 돌아왔어요.",
                color = Muted)
            Action("체험 완료") {
                onResult(TrialResult(0, if (automatic) 0 else 2, 0, source))
            }
        }
    }
}

private enum class PassengerPhase { AwaitArrival, SelectFront, AwaitDeparture, SelectDriver, Complete }

@Composable
private fun BaselineClimatePanel(trial: ClimateTrial, onResult: (TrialResult) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(OrbitBackground).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("앞좌석 공조", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (trial.task.goalSync) ClimateToggle("Auto", trial.auto) { trial.toggleAuto()?.let(onResult) }
            ClimateToggle("SYNC", trial.sync) { trial.toggleSync()?.let(onResult) }
        }
        AdaptivePair(
            first = { modifier ->
                Column(
                    modifier.clip(RoundedCornerShape(12.dp)).background(OrbitPaper).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("운전석", color = Muted)
                    Text(degrees(trial.driverTemperature), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { trial.adjustTemperature(-0.5f)?.let(onResult) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("− 0.5°C") }
                        OutlinedButton(
                            onClick = { trial.adjustTemperature(0.5f)?.let(onResult) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("+ 0.5°C") }
                    }
                }
            },
            second = { modifier ->
                Column(
                    modifier.clip(RoundedCornerShape(12.dp)).background(OrbitPaper).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("동승석", color = Muted)
                    Text(degrees(trial.passengerTemperature), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(if (trial.sync) "운전석과 동기화 중" else "운전석과 독립 조절 중", color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { trial.adjustTemperature(-0.5f, passenger = true)?.let(onResult) }) {
                            Text("− 0.5°C")
                        }
                        OutlinedButton(onClick = { trial.adjustTemperature(0.5f, passenger = true)?.let(onResult) }) {
                            Text("+ 0.5°C")
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ClimateToggle(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        "${label} ${if (enabled) "켜짐" else "꺼짐"}",
        modifier = Modifier.heightIn(min = 48.dp).clip(shape)
            .background(if (enabled) OrbitBlue else Glass)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        color = if (enabled) MaterialTheme.colorScheme.onPrimary else OrbitInk,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Preference(task: ClimateTask, onFinish: (String, String) -> Unit) {
    var choice by remember { mutableStateOf<String?>(null) }
    Intro("어느 화면이 더 편했나요?", "선호도 응답은 선택 사항이에요. 참여 보상은 선택한 화면과 관계없이 같아요.")
    Panel {
        if (choice == null) {
            ChoiceButtons(listOf("A · ${Variant.A.labelFor(task)}", "B · ${Variant.B.labelFor(task)}", "차이 없음")) { choice = it }
        } else {
            Eyebrow("선택  ·  ${choice.orEmpty()}")
            val reasons = if (task == ClimateTask.PassengerArrival) {
                listOf("매번 바꾸는 수고가 줄어요", "바뀌는 시점을 직접 정하고 싶어요", SeparateTemperatureFeedback)
            } else listOf("원하는 기능을 찾기 쉬웠어요", "조작 단계가 적었어요", SeparateTemperatureFeedback)
            ChoiceButtons(reasons) {
                onFinish(choice.orEmpty(), it)
            }
        }
        OutlinedButton(onClick = { onFinish(choice ?: "응답 건너뜀", "") }) { Text("건너뛰고 결과 보기") }
    }
}

@Composable
private fun Insight(comparison: Comparison, award: Int, onPoints: () -> Unit) {
    val difference = comparison.baseline.milliseconds - comparison.preset.milliseconds
    val faster = if (difference > 0) "B안" else "A안"
    val airflowExperiment = comparison.baseline.signal != null || comparison.preset.signal != null
    val timing = if (airflowExperiment) "탑승·하차 시 송풍 범위 변경"
        else if (comparison.task == ClimateTask.PassengerArrival) "이전 탑승 시연 기록"
        else if (abs(difference) < 100) "두 화면의 완료 시간이 비슷했어요."
        else "${faster}이 ${seconds(abs(difference))}초 빨랐어요."
    Intro("공조 체험 결과", "${comparison.task.title} · $timing")
    AdaptivePair(
        first = { modifier -> TrialMetrics("A · ${Variant.A.labelFor(comparison.task)}", comparison.baseline, modifier, comparison.task != ClimateTask.PassengerArrival) },
        second = { modifier -> TrialMetrics("B · ${Variant.B.labelFor(comparison.task)}", comparison.preset, modifier, comparison.task != ClimateTask.PassengerArrival) },
    )
    Panel {
        Eyebrow("개선에 쓰이는 기록")
        Text("내 선호  ·  ${comparison.preference}", style = MaterialTheme.typography.titleMedium)
        if (comparison.reason.isNotEmpty()) Text(comparison.reason, color = Muted)
        Text(
            if (comparison.reason == SeparateTemperatureFeedback) {
                "조작을 줄이면서 두 좌석의 온도를 따로 저장하는 안을 다음 실험에서 비교할 수 있어요."
            } else if (airflowExperiment) {
                "선호와 이유를 함께 보면 출시 전에 자동 전환 시점과 수동 조작이 필요한 상황을 더 살펴볼 수 있어요."
            } else {
                "탭 수, 완료 시간, 오조작과 선호를 함께 보면 어떤 화면을 더 검증할지 판단할 수 있어요."
            },
            color = Muted,
        )
        Text("이 시연 기록은 이 기기에 저장돼요.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
    Panel {
        Eyebrow("MY ORBIT")
        if (award > 0) {
            Text("+${formatPoints(award)} 적립", style = MaterialTheme.typography.headlineSmall, color = OrbitBlue)
            Text("공조 비교 체험 완료 보상이에요.", color = Muted)
        } else {
            Text("공조 비교 기록을 저장했어요.", style = MaterialTheme.typography.titleMedium)
        }
        Action("적립 내역 보기", onClick = onPoints)
    }
}

@Composable
private fun TrialMetrics(label: String, result: TrialResult, modifier: Modifier, showTiming: Boolean) {
    Panel(modifier) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text("${result.touches}탭", style = MaterialTheme.typography.displaySmall)
        if (showTiming) Text("${seconds(result.milliseconds)}초  ·  오조작 ${result.errors}회", color = Muted)
        result.signal?.let { Text(it.label, color = Muted) }
    }
}

@Composable
private fun Points(data: OrbitData, showTitle: Boolean, canReset: Boolean, onReset: () -> Unit) {
    var editingName by remember { mutableStateOf(false) }
    var confirmingReset by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }
    if (confirmingReset && canReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            containerColor = OrbitPaper,
            title = { Text("기록을 초기화할까요?") },
            text = { Text("미션 진행과 완료 내역, 체험 의견, 포인트, 이달 주행 및 리그 기록을 지워요. 저장한 이름과 공조 설정은 유지돼요.") },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    confirmingReset = false
                    resetDone = true
                }) { Text("초기화", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingReset = false }) { Text("취소") } },
        )
    }
    if (editingName) {
        var name by remember { mutableStateOf(data.displayName) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            containerColor = OrbitPaper,
            title = { Text("홈 인사말 이름") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20 && '\n' !in it && '\r' !in it) name = it },
                    label = { Text("이름 또는 별명") },
                    supportingText = { Text("비워 두면 이름 없이 인사해요.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    data.saveDisplayName(name)
                    editingName = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("취소") } },
        )
    }
    Panel {
        if (showTitle) Text(Page.Points.label, style = MaterialTheme.typography.titleLarge)
        Eyebrow("보유 포인트")
        Text("${formatPoints(data.balance)}", style = MaterialTheme.typography.displaySmall)
    }
    Panel {
        Text("최근 참여 내역", style = MaterialTheme.typography.titleLarge)
        if (data.lastMission == null && data.comparison == null && data.otaReview == null && data.chargingOtaReview == null &&
            data.labSubmissions.isEmpty() && data.syncMissionFeedback == null && data.cabinTrialFeedback == null
        ) {
            Text("아직 완료한 미션이나 체험이 없어요.", color = Muted)
        }
        data.lastMission?.let {
            val arrivalDetail = data.lastMissionDestination?.let { destination ->
                val source = if (data.lastMissionSimulated) "모의 안내 도착" else "Pleos 내비 도착 인증"
                "$source  ·  $destination"
            }
            PointActivity("드라이브 · $it", data.lastPlayAward, listOfNotNull(arrivalDetail, data.lastFeedback))
        }
        data.comparison?.let { PointActivity("X-LAB · ${it.task.title}", data.lastLabAward) }
        data.cabinTrialFeedback?.let { PointActivity("장기 미션 · 착석 연동 공조 7일", data.cabinTrialAward, listOf(it)) }
        data.otaReview?.let {
            PointActivity("OTA · 주행 정보 화면 도어 잠금", it.award, listOf(it.rating, it.feedback))
        }
        data.chargingOtaReview?.let {
            PointActivity("OTA · 충전소 안내 업데이트", it.award, listOf("${it.rating} · ${it.feedback}"))
        }
        data.labSubmissions.firstOrNull()?.let {
            PointActivity("X-LAB · ${it.type.title}", it.award, listOf(it.message))
        }
        data.syncMissionFeedback?.let {
            PointActivity("PLAY · 앞좌석 온도 동기화", data.syncMissionAward, listOf(it))
        }
    }
    Panel {
        Text("제휴 제안", style = MaterialTheme.typography.titleMedium)
        Text("블루멤버스 전환과 차량 관리 쿠폰을 제안해요. 실제 사용은 제휴 확정 후 가능해요.", color = Muted)
    }
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("홈 인사말 이름", style = MaterialTheme.typography.titleMedium)
                Text(data.displayName.ifBlank { "설정 안 함" }, color = Muted)
            }
            TextButton(onClick = { editingName = true }, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(if (data.displayName.isBlank()) "이름 설정" else "이름 변경")
            }
        }
    }
    Panel {
        Text("기록 관리", style = MaterialTheme.typography.titleMedium)
        if (resetDone) Text("기록을 초기화했어요. 미션을 다시 시작할 수 있어요.", color = Muted)
        OutlinedButton(onClick = { confirmingReset = true }, enabled = canReset,
            modifier = Modifier.heightIn(min = 56.dp)) {
            Text("기록 초기화")
        }
    }
}

@Composable
private fun PointActivity(title: String, award: Int, details: List<String> = emptyList()) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (award > 0) "+${formatPoints(award)}" else "기록 완료",
                style = MaterialTheme.typography.labelMedium,
                color = if (award > 0) OrbitBlue else Muted,
            )
        }
        details.forEach { Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ChoiceButtons(labels: List<String>, enabled: Boolean = true, onClick: (String) -> Unit) {
    labels.forEach { label ->
        OutlinedButton(onClick = { onClick(label) }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(label, fontSize = 17.sp)
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(OrbitPaper).border(1.dp, Border, shape).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun StatusPill(status: ParkingStatus) {
    val color = if (status == ParkingStatus.Parked) Teal else Muted
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = .1f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(status.label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Chip(label: String) {
    Text(label, Modifier.clip(CircleShape).background(Glass).padding(horizontal = 12.dp, vertical = 7.dp),
        color = OrbitBlue, fontWeight = FontWeight.Bold)
}

@Composable
private fun Eyebrow(label: String) {
    Text(label, color = OrbitBlue, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.6.sp)
}

@Composable
private fun Intro(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Action(label: String, enabled: Boolean = true, minHeight: Dp = 74.dp, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface),
        modifier = Modifier.heightIn(min = minHeight)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

private fun seconds(millis: Long) = String.format(Locale.KOREA, "%.1f", millis / 1000.0)
