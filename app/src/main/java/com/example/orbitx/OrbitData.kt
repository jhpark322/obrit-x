package com.example.orbitx

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal const val LeagueMinimumDistanceKm = 500
private const val PointUnit = 1_000
private const val MonthlyPointLimit = 80_000

internal fun formatPoints(points: Int): String = String.format(Locale.KOREA, "%,d P", points)
internal const val LeagueCourseDistanceKm = 24

internal data class DriveVisit(val day: LocalDate, val destinationKey: String)

internal fun repeatedDriveDays(visits: List<DriveVisit>, today: LocalDate): Int =
    visits.asSequence()
        .filter { it.day in today.minusDays(6)..today }
        .groupBy { it.destinationKey }
        .values.maxOfOrNull { visitsToPlace -> visitsToPlace.distinctBy { it.day }.size } ?: 0

internal data class EvEstimateTotals(val distanceKm: Float, val energyKwh: Float)
internal data class FuelEstimateTotals(val distanceKm: Float, val fuelLiters: Float)

internal fun fuelDrivingInterval(previous: LeagueVehicleSample, current: LeagueVehicleSample): FuelEstimateTotals? {
    val distance = (current.odometerKm ?: return null) - (previous.odometerKm ?: return null)
    val fuel = (previous.fuelLiters ?: return null) - (current.fuelLiters ?: return null)
    return FuelEstimateTotals(distance, fuel).takeIf {
        distance.isFinite() && fuel.isFinite() && distance > 0f && fuel >= 0f
    }
}

internal fun evDrivingInterval(previous: LeagueVehicleSample, current: LeagueVehicleSample): EvEstimateTotals? {
    val distance = (current.odometerKm ?: return null) - (previous.odometerKm ?: return null)
    val energy = (previous.batteryKwh ?: return null) - (current.batteryKwh ?: return null)
    return EvEstimateTotals(distance, energy).takeIf { distance > 0f && energy >= 0f }
}

internal enum class Variant(val title: String) {
    A("Pleos 기본 공조"), B("원터치 컴포트")
}

internal enum class ClimateTask(val title: String, val startingTemperature: Float, val passengerGoal: Float) {
    PassengerArrival("동승석 탑승 시 자동 전환", 23f, 23f),
    SeatHold("동승석 온도 유지", 24f, 24f),
    SyncPreset("앞좌석 한 번에 맞추기", 22f, 23f);

    val goalSync get() = this == SyncPreset
}

internal enum class ClimateSignal(val label: String) {
    Pleos("Pleos 착석 신호"), Simulated("앱 내 착석 시연"), Mixed("Pleos 신호와 앱 내 시연")
}

internal data class TrialResult(
    val milliseconds: Long,
    val touches: Int,
    val errors: Int,
    val signal: ClimateSignal? = null,
)

internal data class Comparison(
    val baseline: TrialResult,
    val preset: TrialResult,
    val preference: String,
    val reason: String,
    val task: ClimateTask,
) {
    fun result(variant: Variant) = if (variant == Variant.A) baseline else preset
}

internal class ClimateTrial(val variant: Variant, val task: ClimateTask) {
    private val startedAt = SystemClock.elapsedRealtime()
    private var touches = 0
    private var errors = 0
    private var completed = false

    var libraryOpen by mutableStateOf(false)
        private set
    var panelOpen by mutableStateOf(false)
        private set
    var driverTemperature by mutableStateOf(task.startingTemperature)
        private set
    var passengerTemperature by mutableStateOf(task.startingTemperature)
        private set
    var auto by mutableStateOf(false)
        private set
    var sync by mutableStateOf(!task.goalSync)
        private set

    fun openLibrary() {
        if (completed || variant != Variant.A || task.goalSync || libraryOpen) return
        libraryOpen = true
        touches++
    }

    fun openPanel() {
        if (completed || variant != Variant.A || panelOpen) return
        if (!task.goalSync && !libraryOpen) return
        panelOpen = true
        touches++
    }

    fun adjustTemperature(amount: Float, passenger: Boolean = false): TrialResult? {
        if (!canEditPanel()) return null
        val previous = if (passenger) passengerTemperature else driverTemperature
        val goal = if (passenger) task.passengerGoal else 23f
        val next = (previous + amount).coerceIn(16f, 30f)
        val changesPassengerUnintentionally = !passenger && sync && !task.goalSync && next != task.passengerGoal
        if (kotlin.math.abs(next - goal) >= kotlin.math.abs(previous - goal) || changesPassengerUnintentionally) errors++
        if (passenger) {
            passengerTemperature = next
            sync = false
        } else {
            driverTemperature = next
            if (sync) passengerTemperature = next
        }
        return record()
    }

    fun toggleAuto(): TrialResult? {
        if (!canEditPanel()) return null
        auto = !auto
        if (!auto) errors++
        return record()
    }

    fun toggleSync(): TrialResult? {
        if (!canEditPanel()) return null
        sync = !sync
        if (sync) passengerTemperature = driverTemperature
        if (sync != task.goalSync) errors++
        return record()
    }

    fun applyPreset(): TrialResult? {
        if (completed || variant != Variant.B) return null
        driverTemperature = 23f
        passengerTemperature = task.passengerGoal
        if (task.goalSync) auto = true
        sync = task.goalSync
        return record()
    }

    private fun canEditPanel() = !completed && variant == Variant.A && panelOpen

    private fun record(): TrialResult? {
        touches++
        if (driverTemperature != 23f || passengerTemperature != task.passengerGoal || sync != task.goalSync) return null
        if (task.goalSync && !auto) return null
        completed = true
        return TrialResult(SystemClock.elapsedRealtime() - startedAt, touches, errors)
    }
}

internal data class OtaReview(val rating: String, val feedback: String, val award: Int)
internal data class LinkedMissionDestination(val name: String, val address: String, val simulated: Boolean) {
    val label get() = name.ifBlank { address }
}

internal data class MissionCompletion(val title: String, val award: Int, val destination: String?, val simulated: Boolean)

internal enum class LabSubmissionType(val title: String, val prompt: String) {
    Idea("아이디어 제안", "어떤 상황에서 어떤 기능이 필요했나요?"),
    Feedback("체험 피드백", "어떤 기능을 써봤고 무엇이 좋거나 불편했나요?"),
}

internal data class LabSubmission(val type: LabSubmissionType, val message: String, val date: String, val award: Int)

internal class OrbitData(context: Context) {
    private val prefs = context.getSharedPreferences("orbit-local", Context.MODE_PRIVATE)

    init {
        if (!prefs.getBoolean("thousandPointScale", false)) {
            val editor = prefs.edit()
            for (key in listOf("balance", "monthPoints", "lastPlayAward", "lastLabAward", "pendingArrivalAward", "otaChargingCardAward")) {
                if (prefs.contains(key)) editor.putInt(key, prefs.getInt(key, 0) * 100)
            }
            val submissions = prefs.getString("labSubmissions", null)
                ?.let { runCatching { JSONArray(it) }.getOrNull() }
            if (submissions != null) {
                for (index in 0 until submissions.length()) {
                    submissions.optJSONObject(index)?.let { it.put("award", it.optInt("award") * 100) }
                }
                editor.putString("labSubmissions", submissions.toString())
            }
            editor.putBoolean("thousandPointScale", true).apply()
        }
        if (!prefs.getBoolean("flatDriveAward", false)) {
            val editor = prefs.edit()
            val lastAward = prefs.getInt("lastPlayAward", 0)
            val excess = (lastAward - PointUnit).coerceAtLeast(0)
            if (excess > 0) {
                editor.putInt("lastPlayAward", PointUnit)
                editor.putInt("balance", (prefs.getInt("balance", 0) - excess).coerceAtLeast(0))
                val week = prefs.getString("missionWeek", null)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val month = prefs.getString("month", null)
                if (week != null && YearMonth.from(week).toString() == month &&
                    YearMonth.from(week.plusDays(6)).toString() == month
                ) {
                    editor.putInt("monthPoints", (prefs.getInt("monthPoints", 0) - excess).coerceAtLeast(0))
                }
            }
            if (prefs.getInt("pendingArrivalAward", 0) > PointUnit) {
                editor.putInt("pendingArrivalAward", PointUnit)
            }
            editor.putBoolean("flatDriveAward", true).apply()
        }
    }

    var displayName by mutableStateOf(prefs.getString("displayName", "").orEmpty())
        private set

    fun saveDisplayName(value: String) {
        displayName = value.trim()
        prefs.edit().putString("displayName", displayName).apply()
    }

    var balance by mutableStateOf(prefs.getInt("balance", 0))
        private set
    var automaticCabinClimate by mutableStateOf(prefs.getBoolean("automaticCabinClimate", false))
        private set
    var cabinTrialDays by mutableStateOf(prefs.getStringSet("cabinTrialDays", emptySet()).orEmpty().toSet())
        private set
    var cabinTrialFeedback by mutableStateOf(prefs.getString("cabinTrialFeedback", null))
        private set
    var cabinTrialAward by mutableStateOf(prefs.getInt("cabinTrialAward", 0))
        private set
    val cabinTrialGoal = 7
    val availableCabinTrialAward get() = capMonthly(3 * PointUnit, LocalDate.now())

    fun saveAutomaticCabinClimate(enabled: Boolean) {
        automaticCabinClimate = enabled
        prefs.edit().putBoolean("automaticCabinClimate", enabled).apply()
    }

    fun recordCabinTrialDay() {
        if (!automaticCabinClimate || cabinTrialDays.size >= cabinTrialGoal) return
        val day = LocalDate.now().toString()
        if (day in cabinTrialDays) return
        cabinTrialDays = cabinTrialDays + day
        prefs.edit().putStringSet("cabinTrialDays", cabinTrialDays).apply()
    }

    fun completeCabinTrial(feedback: String) {
        if (cabinTrialDays.size < cabinTrialGoal || cabinTrialFeedback != null) return
        val today = LocalDate.now()
        val award = availableCabinTrialAward
        prefs.edit().putString("cabinTrialFeedback", feedback).putInt("cabinTrialAward", award)
            .apply { storeAward(this, award, today) }.apply()
        cabinTrialFeedback = feedback
        cabinTrialAward = award
        balance += award
    }

    var lastDriveDay by mutableStateOf(prefs.getString("lastDriveDay", null))
        private set
    var activeMission by mutableStateOf(prefs.getString("activeMission", null))
        private set
    var lastMission by mutableStateOf(prefs.getString("lastMission", null))
        private set
    var lastMissionDestination by mutableStateOf(prefs.getString("lastMissionDestination", null))
        private set
    var lastMissionSimulated by mutableStateOf(prefs.getBoolean("lastMissionSimulated", false))
        private set
    var linkedMissionDestination by mutableStateOf(
        prefs.getString("missionDestinationName", null)?.let { name ->
            LinkedMissionDestination(
                name, prefs.getString("missionDestinationAddress", "").orEmpty(),
                prefs.getBoolean("missionDestinationSimulated", false),
            )
        }?.takeIf { activeMission != null },
    )
        private set
    var lastFeedback by mutableStateOf(prefs.getString("lastFeedback", null))
        private set
    var lastPlayAward by mutableStateOf(prefs.getInt("lastPlayAward", 0))
        private set
    var pendingMissionArrival by mutableStateOf(
        prefs.getString("pendingArrivalTitle", null)?.let {
            MissionCompletion(it, prefs.getInt("pendingArrivalAward", 0),
                prefs.getString("pendingArrivalDestination", null), prefs.getBoolean("pendingArrivalSimulated", false))
        },
    )
        private set
    var lastLabAward by mutableStateOf(prefs.getInt("lastLabAward", 0))
        private set
    var comparison by mutableStateOf(loadComparison())
        private set
    var leagueSeason by mutableStateOf(YearMonth.now())
        private set
    var leagueEnergyAvailable by mutableStateOf<Boolean?>(null)
        private set
    var monthlyVehicleDistanceKm by mutableStateOf<Float?>(null)
        private set
    private var lastLeagueSample: LeagueVehicleSample? = null
    private var lastSampleAt = 0L
    private var lastSampleMonth: YearMonth? = null
    private var evEstimateMonth by mutableStateOf(prefs.getString("evEstimateMonth", null))
    private var evEstimate by mutableStateOf(
        EvEstimateTotals(prefs.getFloat("evEstimateKm", 0f), prefs.getFloat("evEstimateKwh", 0f)),
    )
    val monthlyEvEstimate get() = evEstimate.takeIf { evEstimateMonth == leagueSeason.toString() }
    private var fuelEstimateMonth by mutableStateOf(prefs.getString("fuelEstimateMonth", null))
    private var fuelEstimate by mutableStateOf(
        FuelEstimateTotals(prefs.getFloat("fuelEstimateKm", 0f), prefs.getFloat("fuelEstimateLiters", 0f)),
    )
    val monthlyFuelEstimate get() = fuelEstimate.takeIf { fuelEstimateMonth == leagueSeason.toString() }
    private var leagueDemoSeason by mutableStateOf(
        prefs.getString("leagueDemoSeason", null) ?: if (prefs.getBoolean("hasLeagueDemo", false)) {
            leagueSeason.toString().also {
                prefs.edit().putString("leagueDemoSeason", it)
                    .putInt("leagueDemoDistanceKm", LeagueCourseDistanceKm).remove("hasLeagueDemo").apply()
            }
        } else null,
    )
    private var leagueDemoDistance by mutableStateOf(
        prefs.getInt("leagueDemoDistanceKm", if (leagueDemoSeason == null) 0 else LeagueCourseDistanceKm),
    )
    val leagueDistanceKm get() = if (leagueDemoSeason == leagueSeason.toString()) leagueDemoDistance else 0
    val hasLeagueDemo get() = leagueDistanceKm >= LeagueMinimumDistanceKm
    var syncMissionFeedback by mutableStateOf(prefs.getString("syncMissionFeedback", null))
        private set
    var syncMissionAward by mutableStateOf(prefs.getInt("syncMissionAward", 0))
        private set
    val availableSyncAward: Int
        get() = if (syncMissionFeedback == null) capMonthly(PointUnit, LocalDate.now()) else 0
    var chargingOtaReview by mutableStateOf(loadOtaReview("otaChargingCard"))
        private set
    var otaReview by mutableStateOf(loadOtaReview("otaDriveViewDoorLock"))
        private set
    var labSubmissions by mutableStateOf(loadLabSubmissions())
        private set

    fun resetActivityRecords() {
        prefs.edit().clear()
            .putString("displayName", displayName)
            .putBoolean("automaticCabinClimate", automaticCabinClimate)
            .putBoolean("thousandPointScale", true)
            .putBoolean("flatDriveAward", true)
            .apply()

        balance = 0
        activeMission = null
        linkedMissionDestination = null
        pendingMissionArrival = null
        lastDriveDay = null
        lastMission = null
        lastMissionDestination = null
        lastMissionSimulated = false
        lastFeedback = null
        lastPlayAward = 0
        comparison = null
        lastLabAward = 0
        cabinTrialDays = emptySet()
        cabinTrialFeedback = null
        cabinTrialAward = 0
        syncMissionFeedback = null
        syncMissionAward = 0
        otaReview = null
        chargingOtaReview = null
        labSubmissions = emptyList()

        leagueDemoSeason = null
        leagueDemoDistance = 0
        monthlyVehicleDistanceKm = null
        leagueEnergyAvailable = null
        lastLeagueSample = null
        lastSampleAt = 0
        lastSampleMonth = null
        evEstimateMonth = null
        evEstimate = EvEstimateTotals(0f, 0f)
        fuelEstimateMonth = null
        fuelEstimate = FuelEstimateTotals(0f, 0f)
        refreshLeagueSeason()
    }

    val availableSubmissionAward: Int
        get() = submissionAward(LocalDate.now())

    fun submitLabFeedback(type: LabSubmissionType, message: String): LabSubmission? {
        val body = message.trim()
        if (body.length !in 5..240 || labSubmissions.firstOrNull()?.let { it.type == type && it.message == body } == true) {
            return null
        }
        val today = LocalDate.now()
        val submission = LabSubmission(type, body, today.toString(), submissionAward(today))
        val history = (listOf(submission) + labSubmissions).take(12)
        val encoded = JSONArray()
        history.forEach {
            encoded.put(JSONObject().put("type", it.type.name).put("message", it.message)
                .put("date", it.date).put("award", it.award))
        }
        prefs.edit()
            .putString("labSubmissions", encoded.toString())
            .putString("labSubmissionDate", today.toString())
            .apply { storeAward(this, submission.award, today) }
            .apply()
        labSubmissions = history
        balance += submission.award
        return submission
    }

    private fun loadLabSubmissions(): List<LabSubmission> {
        val raw = prefs.getString("labSubmissions", null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val type = LabSubmissionType.entries.firstOrNull { it.name == entry.optString("type") } ?: return@mapNotNull null
            LabSubmission(type, entry.optString("message"), entry.optString("date"), entry.optInt("award"))
        }
    }

    private fun submissionAward(today: LocalDate) =
        if (prefs.getString("labSubmissionDate", null) == today.toString()) 0 else capMonthly(PointUnit, today)

    private fun loadOtaReview(key: String): OtaReview? = prefs.getString("${key}Rating", null)?.let {
        OtaReview(it, prefs.getString("${key}Feedback", "").orEmpty(), prefs.getInt("${key}Award", 0))
    }

    fun finishOtaReview(rating: String, feedback: String) {
        if (otaReview != null) return
        val today = LocalDate.now()
        val award = availableOtaAward
        prefs.edit()
            .putString("otaDriveViewDoorLockRating", rating)
            .putString("otaDriveViewDoorLockFeedback", feedback)
            .putInt("otaDriveViewDoorLockAward", award)
            .apply { storeAward(this, award, today) }
            .apply()
        otaReview = OtaReview(rating, feedback, award)
        balance += award
    }

    val availableOtaAward: Int
        get() = if (otaReview == null) capMonthly(2 * PointUnit, LocalDate.now()) else 0

    val availableDriveAward: Int
        get() {
            val today = LocalDate.now()
            return capMonthly(if (weeklyDriveCount(today) < 2) PointUnit else 0, today)
        }

    fun refreshLeagueSeason() {
        val month = YearMonth.now()
        if (leagueSeason != month) monthlyVehicleDistanceKm = null
        leagueSeason = month
    }

    fun leagueDistanceFromOdometer(odometerKm: Float): Float? {
        if (!odometerKm.isFinite() || odometerKm < 0) return null
        refreshLeagueSeason()
        val month = leagueSeason.toString()
        if (prefs.getString("vehicleLeagueMonth", null) != month) {
            prefs.edit().putString("vehicleLeagueMonth", month)
                .putFloat("vehicleLeagueStartKm", odometerKm).apply()
        }
        val baseline = prefs.getFloat("vehicleLeagueStartKm", odometerKm)
        return (odometerKm - baseline).takeIf { it >= 0 }
    }

    fun recordLeagueSample(sample: LeagueVehicleSample?) {
        refreshLeagueSeason()
        val now = SystemClock.elapsedRealtime()
        val previous = lastLeagueSample.takeIf {
            lastSampleMonth == leagueSeason && now - lastSampleAt in 1L..15_000L
        }
        lastLeagueSample = sample
        lastSampleAt = now
        lastSampleMonth = leagueSeason
        leagueEnergyAvailable = sample?.let { it.odometerKm != null && it.batteryKwh != null }
        sample?.odometerKm?.let { monthlyVehicleDistanceKm = leagueDistanceFromOdometer(it) }
        if (previous == null || sample == null) return
        fuelDrivingInterval(previous, sample)?.let { interval ->
            val total = monthlyFuelEstimate
            val updated = FuelEstimateTotals(
                (total?.distanceKm ?: 0f) + interval.distanceKm,
                (total?.fuelLiters ?: 0f) + interval.fuelLiters,
            )
            val month = leagueSeason.toString()
            prefs.edit().putString("fuelEstimateMonth", month)
                .putFloat("fuelEstimateKm", updated.distanceKm)
                .putFloat("fuelEstimateLiters", updated.fuelLiters).apply()
            fuelEstimateMonth = month
            fuelEstimate = updated
        }
        val interval = evDrivingInterval(previous, sample) ?: return
        val total = monthlyEvEstimate
        val updated = EvEstimateTotals(
            (total?.distanceKm ?: 0f) + interval.distanceKm,
            (total?.energyKwh ?: 0f) + interval.energyKwh,
        )
        val month = leagueSeason.toString()
        prefs.edit().putString("evEstimateMonth", month)
            .putFloat("evEstimateKm", updated.distanceKm)
            .putFloat("evEstimateKwh", updated.energyKwh)
            .apply()
        evEstimateMonth = month
        evEstimate = updated
    }

    fun recordLeagueDemo() {
        refreshLeagueSeason()
        val season = leagueSeason.toString()
        if (hasLeagueDemo) return
        val distance = leagueDistanceKm + LeagueCourseDistanceKm
        prefs.edit().putString("leagueDemoSeason", season).putInt("leagueDemoDistanceKm", distance).apply()
        leagueDemoSeason = season
        leagueDemoDistance = distance
    }

    fun finishSyncMission(reason: String) {
        if (syncMissionFeedback != null) return
        val today = LocalDate.now()
        val award = capMonthly(PointUnit, today)
        prefs.edit().putString("syncMissionFeedback", reason).putInt("syncMissionAward", award)
            .apply { storeAward(this, award, today) }.apply()
        syncMissionFeedback = reason
        syncMissionAward = award
        balance += award
    }

    val firstVariant: Variant
        get() = if (prefs.getBoolean("comfortPresetFirst", false)) Variant.B else Variant.A

    val availableLabAward: Int
        get() = labAward(LocalDate.now())

    fun selectDriveMode(intent: String) {
        prefs.edit().putString("activeMission", intent).apply()
        activeMission = intent
    }

    fun recentRepeatedDriveDays(): Int = repeatedDriveDays(loadDriveVisits(), LocalDate.now())

    private fun loadDriveVisits(): List<DriveVisit> {
        val entries = runCatching { JSONArray(prefs.getString("verifiedDriveVisits", "[]")) }.getOrNull()
            ?: return emptyList()
        return (0 until entries.length()).mapNotNull { index ->
            val entry = entries.optJSONObject(index) ?: return@mapNotNull null
            val day = runCatching { LocalDate.parse(entry.optString("day")) }.getOrNull()
                ?: return@mapNotNull null
            DriveVisit(day, entry.optString("key"))
        }
    }

    fun linkMissionDestination(target: LinkedMissionDestination?) {
        if (target != null && activeMission == null) return
        prefs.edit()
            .putString("missionDestinationName", target?.name)
            .putString("missionDestinationAddress", target?.address)
            .putBoolean("missionDestinationSimulated", target?.simulated == true)
            .apply()
        linkedMissionDestination = target
    }

    fun finishMission(feedback: String?, destination: LinkedMissionDestination? = null): MissionCompletion? {
        val intent = activeMission ?: return null
        if (destination != null && destination != linkedMissionDestination) return null
        val destinationLabel = destination?.label
        val simulated = destination?.simulated == true
        val today = LocalDate.now()
        val week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        val count = weeklyDriveCount(today)
        val award = availableDriveAward
        prefs.edit()
            .remove("activeMission")
            .remove("missionDestinationName")
            .remove("missionDestinationAddress")
            .remove("missionDestinationSimulated")
            .putString("lastMission", intent)
            .putString("lastDriveDay", today.toString())
            .putString("lastMissionDestination", destinationLabel)
            .putBoolean("lastMissionSimulated", simulated)
            .putString("lastFeedback", feedback)
            .putString("missionWeek", week)
            .putInt("missionCount", count + 1)
            .putInt("lastPlayAward", award)
            .apply {
                if (destination != null) {
                    putString("pendingArrivalTitle", intent)
                    putInt("pendingArrivalAward", award)
                    putString("pendingArrivalDestination", destinationLabel)
                    putBoolean("pendingArrivalSimulated", simulated)
                    if (!simulated) {
                        val normalized = destination.address.ifBlank { destination.name }
                            .trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
                        if (normalized.isNotBlank()) {
                            val key = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
                                .joinToString("") { "%02x".format(it) }
                            val visits = (loadDriveVisits() + DriveVisit(today, key))
                                .filter { it.day in today.minusDays(6)..today }.distinct()
                            val entries = JSONArray()
                            visits.forEach { entries.put(JSONObject().put("day", it.day.toString()).put("key", it.destinationKey)) }
                            putString("verifiedDriveVisits", entries.toString())
                        }
                    }
                }
                storeAward(this, award, today)
            }
            .apply()
        activeMission = null
        linkedMissionDestination = null
        lastMission = intent
        lastDriveDay = today.toString()
        lastMissionDestination = destinationLabel
        lastMissionSimulated = simulated
        lastFeedback = feedback
        lastPlayAward = award
        balance += award
        val completion = MissionCompletion(intent, award, destinationLabel, simulated)
        if (destination != null) pendingMissionArrival = completion
        return completion
    }

    fun acknowledgeMissionArrival() {
        prefs.edit().remove("pendingArrivalTitle").remove("pendingArrivalAward")
            .remove("pendingArrivalDestination").remove("pendingArrivalSimulated").apply()
        pendingMissionArrival = null
    }

    fun finishExperiment(result: Comparison) {
        val today = LocalDate.now()
        val award = labAward(today)
        prefs.edit()
            .putBoolean("comfortPresetFirst", firstVariant == Variant.A)
            .putLong("comfortBaselineMillis", result.baseline.milliseconds)
            .putInt("comfortBaselineTouches", result.baseline.touches)
            .putInt("comfortBaselineErrors", result.baseline.errors)
            .putLong("comfortPresetMillis", result.preset.milliseconds)
            .putInt("comfortPresetTouches", result.preset.touches)
            .putInt("comfortPresetErrors", result.preset.errors)
            .putString("comfortBaselineSignal", result.baseline.signal?.name)
            .putString("comfortPresetSignal", result.preset.signal?.name)
            .putString("comfortPreference", result.preference)
            .putString("comfortReason", result.reason)
            .putString("comfortTask", result.task.name)
            .putInt("lastLabAward", award)
            .apply {
                storeAward(this, award, today)
            }
            .apply()
        comparison = result
        lastLabAward = award
        balance += award
    }

    private fun loadComparison(): Comparison? {
        if (!prefs.contains("comfortBaselineMillis")) return null
        return Comparison(
            baseline = TrialResult(
                prefs.getLong("comfortBaselineMillis", 0),
                prefs.getInt("comfortBaselineTouches", 0),
                prefs.getInt("comfortBaselineErrors", 0),
                ClimateSignal.entries.firstOrNull { it.name == prefs.getString("comfortBaselineSignal", null) },
            ),
            preset = TrialResult(
                prefs.getLong("comfortPresetMillis", 0),
                prefs.getInt("comfortPresetTouches", 0),
                prefs.getInt("comfortPresetErrors", 0),
                ClimateSignal.entries.firstOrNull { it.name == prefs.getString("comfortPresetSignal", null) },
            ),
            preference = prefs.getString("comfortPreference", "응답 건너뜀").orEmpty(),
            reason = prefs.getString("comfortReason", "").orEmpty(),
            task = ClimateTask.entries.firstOrNull { it.name == prefs.getString("comfortTask", null) }
                ?: ClimateTask.SyncPreset,
        )
    }

    private fun monthPoints(today: LocalDate): Int {
        return if (prefs.getString("month", null) == today.toString().take(7)) {
            prefs.getInt("monthPoints", 0)
        } else {
            0
        }
    }

    private fun capMonthly(points: Int, today: LocalDate) =
        points.coerceAtMost(((MonthlyPointLimit - monthPoints(today)).coerceAtLeast(0) / PointUnit) * PointUnit)

    private fun weeklyDriveCount(today: LocalDate): Int {
        val week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        return if (prefs.getString("missionWeek", null) == week) prefs.getInt("missionCount", 0) else 0
    }

    private fun labAward(today: LocalDate) =
        if (comparison == null && !prefs.contains("menuMillis")) capMonthly(PointUnit, today) else 0

    private fun storeAward(editor: android.content.SharedPreferences.Editor, award: Int, today: LocalDate) {
        editor.putInt("balance", balance + award)
            .putString("month", today.toString().take(7))
            .putInt("monthPoints", monthPoints(today) + award)
    }
}
