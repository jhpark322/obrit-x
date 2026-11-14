package com.example.orbitx

import android.content.Context
import android.os.SystemClock
import android.os.UserManager
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
internal const val LeagueMinimumEnergyKwh = .2f
internal const val LeagueMinimumFuelLiters = .5f

internal fun leagueEfficiency(distanceKm: Float?, consumed: Float?, minimum: Float): Double? {
    if (distanceKm == null || !distanceKm.isFinite() || distanceKm <= 0f ||
        consumed == null || !consumed.isFinite() || consumed < minimum
    ) return null
    return distanceKm.toDouble() / consumed
}

/** What the drive screen recommended, what the driver actually chose, and the vehicle context behind it. */
internal data class RecommendationRecord(val recommended: String, val chosen: String, val reason: String) {
    val accepted: Boolean get() = recommended == chosen
}

/** One drive, measured from the odometer and battery energy at departure (P) and arrival (P). */
/** Below this the arrival battery counts as matching the prediction. */
internal const val ArrivalGapNoticeKwh = .05f

internal data class TripReport(
    val mode: String,
    val distanceKm: Float?,
    val energyKwh: Float?,
    val charged: Boolean,
    val pending: Boolean,
    /** Battery energy (kWh) Pleos navigation expected at the destination when guidance started. */
    val predictedArrivalKwh: Float? = null,
    /** Battery energy (kWh) the car reported on arrival. */
    val actualArrivalKwh: Float? = null,
) {
    /** kWh used beyond the prediction (negative: arrived with more than predicted). */
    val arrivalGapKwh: Float?
        get() = if (charged || predictedArrivalKwh == null || actualArrivalKwh == null) null
            else predictedArrivalKwh - actualArrivalKwh

    val efficiency: Double?
        get() = if (distanceKm != null && energyKwh != null && distanceKm >= .5f && energyKwh >= .1f)
            (distanceKm / energyKwh).toDouble() else null
}

internal data class LeagueEntry(val name: String, val efficiency: Double, val isMine: Boolean = false)

internal fun leaguePreviewRanking(
    examples: List<Pair<String, Double>>,
    monthlyDistanceKm: Float?,
    efficiency: Double?,
    displayName: String,
): List<LeagueEntry> {
    val ranking = examples.map { (name, value) -> LeagueEntry(name, value) }
    if (monthlyDistanceKm == null || !monthlyDistanceKm.isFinite() ||
        monthlyDistanceKm < LeagueMinimumDistanceKm || efficiency == null ||
        !efficiency.isFinite() || efficiency <= 0.0
    ) return ranking
    return (ranking + LeagueEntry(displayName.ifBlank { "내 기록" }, efficiency, isMine = true))
        .sortedByDescending { it.efficiency }
}
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
    return EvEstimateTotals(distance, energy).takeIf {
        distance.isFinite() && energy.isFinite() && distance > 0f && energy > 0f
    }
}

internal class EvIntervalTracker {
    private data class TimedSample(val value: LeagueVehicleSample, val at: Long)

    private var previous: TimedSample? = null
    private var pending: TimedSample? = null

    fun reset() {
        previous = null
        pending = null
    }

    fun record(sample: LeagueVehicleSample?, now: Long): EvEstimateTotals? {
        val km = sample?.odometerKm
        val kwh = sample?.batteryKwh
        if (sample == null || sample.chargeSwitchOn == true || km == null || !km.isFinite() || km < 0f ||
            kwh == null || !kwh.isFinite() || kwh < 0f
        ) {
            reset()
            return null
        }

        val last = previous
        previous = TimedSample(sample, now)
        if (last == null || now - last.at !in 1L..15_000L ||
            km < last.value.odometerKm!! || kwh > last.value.batteryKwh!!
        ) {
            pending = null
            return null
        }
        if (pending?.let { now - it.at > 30_000L } == true) pending = null
        if (km == last.value.odometerKm && kwh == last.value.batteryKwh) return null

        // Distance and battery properties can arrive on different polls.
        val baseline = pending ?: TimedSample(last.value, now).also { pending = it }
        val interval = evDrivingInterval(baseline.value, sample) ?: return null
        pending = null
        return interval
    }
}

internal enum class Variant(val title: String) {
    A("Pleos 기본 공조"), B("원터치 컴포트")
}

internal enum class ClimateTask(val title: String, val startingTemperature: Float, val passengerGoal: Float) {
    PassengerArrival("동승석 탑승 시 자동 전환", 23f, 23f),
    SeatHold("동승석 온도 유지", 24f, 24f),
    SyncPreset("동승자 탑승 공조 비교", 22f, 23f);

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

internal data class OtaReview(val rating: String, val feedback: String, val award: Int)
internal data class LinkedMissionDestination(
    val name: String,
    val address: String,
    val simulated: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val label get() = name.ifBlank { address }
}

/** One remembered drive: where and when, how efficient, and a one-tap mood line. */
internal data class DriveJournalEntry(
    val day: LocalDate,
    val mode: String,
    val place: String?,
    val distanceKm: Float?,
    val efficiency: Double?,
    val mood: String?,
    val latitude: Double?,
    val longitude: Double?,
)

internal val DriveMoodOptions = listOf("또 오고 싶어요", "함께라서 즐거웠어요", "조용히 쉬었어요", "생각보다 아쉬웠어요")

internal data class MissionCompletion(val title: String, val award: Int, val destination: String?, val simulated: Boolean)

internal enum class LabSubmissionType(val title: String, val prompt: String) {
    Idea("아이디어 제안", "어떤 상황에서 어떤 기능이 필요했나요?"),
    Feedback("체험 피드백", "어떤 기능을 써봤고 무엇이 좋거나 불편했나요?"),
}

internal data class LabSubmission(val type: LabSubmissionType, val message: String, val date: String, val award: Int)

internal class OrbitData(private val context: Context) {
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

    /** Name of the signed-in Pleos profile (the Android user), used unless the driver sets one in MY ORBIT. */
    val profileName: String = runCatching {
        context.getSystemService(UserManager::class.java)?.userName.orEmpty().trim()
    }.getOrDefault("")
    private var customName by mutableStateOf(prefs.getString("displayName", "").orEmpty())
    val displayName: String get() = customName.ifBlank { profileName }
    val usesProfileName: Boolean get() = customName.isBlank()

    fun saveDisplayName(value: String) {
        customName = value.trim()
        prefs.edit().putString("displayName", customName).apply()
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
                prefs.getString("missionDestinationLat", null)?.toDoubleOrNull(),
                prefs.getString("missionDestinationLon", null)?.toDoubleOrNull(),
            )
        }?.takeIf { activeMission != null },
    )
        private set
    var lastFeedback by mutableStateOf(prefs.getString("lastFeedback", null))
        private set
    var lastTrip by mutableStateOf(loadTripReport())
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
    private val evIntervalTracker = EvIntervalTracker()
    private var evEstimateMonth by mutableStateOf(prefs.getString("evEstimateMonth", null))
    private var evEstimate by mutableStateOf(
        EvEstimateTotals(prefs.getFloat("evEstimateKm", 0f), prefs.getFloat("evEstimateKwh", 0f)),
    )
    val monthlyEvEstimate get() = evEstimate.takeIf {
        evEstimateMonth == leagueSeason.toString() && it.energyKwh > 0f
    }
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
    var otaReview by mutableStateOf(loadOtaReview("otaDriveViewDoorLock"))
        private set
    var labSubmissions by mutableStateOf(loadLabSubmissions())
        private set

    fun resetActivityRecords() {
        prefs.edit().clear()
            .putString("displayName", customName)
            .putBoolean("automaticCabinClimate", automaticCabinClimate)
            .putBoolean("thousandPointScale", true)
            .putBoolean("flatDriveAward", true)
            .apply()

        balance = 0
        lastTrip = null
        arrivalChecks = 0
        arrivalGapAverageKwh = null
        driveJournal = emptyList()
        lastRecommendation = null
        recommendationAccepted = 0
        recommendationTotal = 0
        routeSuggestion = null
        routeSuggestionAccepted = null
        activeMission = null
        linkedMissionDestination = null
        pendingMissionArrival = null
        lastDriveDay = null
        lastMission = null
        lastMissionDestination = null
        lastMissionSimulated = false
        lastFeedback = null
        tripExperimentId = null
        tripExperimentAnswer = null
        tripExperimentPrompted = false
        experimentResponses = emptyMap()
        lastPlayAward = 0
        comparison = null
        lastLabAward = 0
        cabinTrialDays = emptySet()
        cabinTrialFeedback = null
        cabinTrialAward = 0
        syncMissionFeedback = null
        syncMissionAward = 0
        otaReview = null
        labSubmissions = emptyList()

        leagueDemoSeason = null
        leagueDemoDistance = 0
        monthlyVehicleDistanceKm = null
        leagueEnergyAvailable = null
        lastLeagueSample = null
        lastSampleAt = 0
        lastSampleMonth = null
        evIntervalTracker.reset()
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
        if (leagueSeason != month) {
            monthlyVehicleDistanceKm = null
            evIntervalTracker.reset()
        }
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
        if (sample?.odometerKm != null) {
            if (activeMission != null && prefs.getBoolean("tripStartPending", false)) storeTripStart(sample)
            if (lastTrip?.pending == true) finishTripReport(sample)
        }
        val fuelInterval = if (previous != null && sample != null) fuelDrivingInterval(previous, sample) else null
        fuelInterval?.let { interval ->
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
        val interval = evIntervalTracker.record(sample, now) ?: return
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


    val availableLabAward: Int
        get() = labAward(LocalDate.now())

    /** Drive mode the drive screen last recommended; used when a drive starts from Pleos navigation. */
    var recommendedDriveMode: String = prefs.getString("recommendedDriveMode", null) ?: "기분 전환"
        set(value) {
            if (field == value) return
            field = value
            prefs.edit().putString("recommendedDriveMode", value).apply()
        }

    /** Why the drive screen recommended [recommendedDriveMode] (the vehicle context behind it). */
    var recommendedReason: String = prefs.getString("recommendedReason", null) ?: ""
        set(value) {
            if (field == value) return
            field = value
            prefs.edit().putString("recommendedReason", value).apply()
        }

    /** Recommended vs. chosen mode for the latest drive, plus running acceptance counts. */
    var lastRecommendation by mutableStateOf(
        prefs.getString("recoShown", null)?.let { shown ->
            RecommendationRecord(shown, prefs.getString("recoChosen", shown).orEmpty(), prefs.getString("recoReason", "").orEmpty())
        },
    )
        private set
    var recommendationAccepted by mutableStateOf(prefs.getInt("recoAccepted", 0))
        private set
    var recommendationTotal by mutableStateOf(prefs.getInt("recoTotal", 0))
        private set

    fun selectDriveMode(intent: String) {
        val starting = activeMission == null
        prefs.edit().putString("activeMission", intent).apply()
        activeMission = intent
        if (starting) {
            val record = RecommendationRecord(recommendedDriveMode, intent, recommendedReason)
            val accepted = recommendationAccepted + if (record.accepted) 1 else 0
            val total = recommendationTotal + 1
            prefs.edit().putString("recoShown", record.recommended).putString("recoChosen", record.chosen)
                .putString("recoReason", record.reason).putInt("recoAccepted", accepted).putInt("recoTotal", total).apply()
            lastRecommendation = record
            recommendationAccepted = accepted
            recommendationTotal = total
        }
        if (starting) {
            prefs.edit().remove("tripPredictKwh").apply()
            val sample = freshVehicleSample()
            if (sample != null) storeTripStart(sample)
            else prefs.edit().putBoolean("tripStartPending", true).remove("tripStartKm").remove("tripStartKwh").apply()
        }
    }

    /** Latest odometer/battery reading if it is recent enough to stand for "now". */
    private fun freshVehicleSample() = lastLeagueSample?.takeIf {
        it.odometerKm != null && SystemClock.elapsedRealtime() - lastSampleAt <= 10_000L
    }

    /**
     * Keeps the first arrival-battery prediction navigation gives during a drive, i.e. the one made at
     * departure, so it can be checked against the battery the car actually reports on arrival.
     */
    /** How many drives compared navigation's arrival prediction with the real arrival, and the mean gap. */
    var arrivalChecks by mutableStateOf(prefs.getInt("arrivalChecks", 0))
        private set
    var arrivalGapAverageKwh by mutableStateOf(
        prefs.getInt("arrivalChecks", 0).takeIf { it > 0 }?.let { prefs.getFloat("arrivalGapSum", 0f) / it },
    )
        private set

    fun notePredictedArrival(kwh: Float?) {
        if (kwh == null || !kwh.isFinite() || kwh <= 0f || activeMission == null || prefs.contains("tripPredictKwh")) return
        // A prediction above the battery we start with is stale (made for an earlier state); skip it.
        val now = freshVehicleSample()?.batteryKwh
        if (now != null && kwh > now + ArrivalGapNoticeKwh) return
        prefs.edit().putFloat("tripPredictKwh", kwh).apply()
    }

    private fun storeTripStart(sample: LeagueVehicleSample) {
        prefs.edit().putBoolean("tripStartPending", false)
            .putFloat("tripStartKm", sample.odometerKm ?: return)
            .putFloat("tripStartKwh", sample.batteryKwh ?: -1f)
            .apply()
    }

    /** Closes the trip report with an arrival reading: distance from the odometer, energy from the battery. */
    private fun finishTripReport(end: LeagueVehicleSample) {
        val report = lastTrip ?: return
        val startKm = prefs.getFloat("tripStartKm", -1f).takeIf { it >= 0f }
        val startKwh = prefs.getFloat("tripStartKwh", -1f).takeIf { it >= 0f }
        val distance = if (startKm != null && end.odometerKm != null) (end.odometerKm - startKm).coerceAtLeast(0f) else null
        val charged = startKwh != null && end.batteryKwh != null && end.batteryKwh > startKwh + .05f
        val energy = if (startKwh != null && end.batteryKwh != null && !charged) (startKwh - end.batteryKwh).coerceAtLeast(0f) else null
        val predicted = prefs.getFloat("tripPredictKwh", -1f).takeIf { it > 0f }
        val actual = end.batteryKwh
        val done = report.copy(distanceKm = distance, energyKwh = energy, charged = charged, pending = false,
            predictedArrivalKwh = predicted, actualArrivalKwh = actual)
        driveJournal.firstOrNull()?.let { latest ->
            updateLatestJournal(latest.copy(distanceKm = distance, efficiency = done.efficiency))
        }
        prefs.edit().putFloat("tripReportKm", distance ?: -1f).putFloat("tripReportKwh", energy ?: -1f)
            .putBoolean("tripReportCharged", charged).putBoolean("tripReportPending", false)
            .putFloat("tripReportPredict", predicted ?: -1f).putFloat("tripReportActual", actual ?: -1f)
            .remove("tripStartKm").remove("tripStartKwh").remove("tripStartPending").remove("tripPredictKwh").apply()
        lastTrip = done
        done.arrivalGapKwh?.let { gap ->
            val count = arrivalChecks + 1
            val sum = prefs.getFloat("arrivalGapSum", 0f) + gap
            prefs.edit().putInt("arrivalChecks", count).putFloat("arrivalGapSum", sum).apply()
            arrivalChecks = count
            arrivalGapAverageKwh = sum / count
        }
    }

    private fun loadTripReport(): TripReport? {
        val mode = prefs.getString("tripReportMode", null) ?: return null
        return TripReport(
            mode,
            prefs.getFloat("tripReportKm", -1f).takeIf { it >= 0f },
            prefs.getFloat("tripReportKwh", -1f).takeIf { it >= 0f },
            prefs.getBoolean("tripReportCharged", false),
            prefs.getBoolean("tripReportPending", false),
            prefs.getFloat("tripReportPredict", -1f).takeIf { it > 0f },
            prefs.getFloat("tripReportActual", -1f).takeIf { it >= 0f },
        )
    }

    /** Driving-time suggestion shown over navigation, and whether the driver accepted it. */
    var routeSuggestion by mutableStateOf(prefs.getString("routeSuggestion", null))
        private set
    var routeSuggestionAccepted by mutableStateOf(
        if (prefs.contains("routeSuggestionAccepted")) prefs.getBoolean("routeSuggestionAccepted", false) else null,
    )
        private set

    fun recordRouteSuggestion(prompt: String?, accepted: Boolean?) {
        prefs.edit().apply {
            if (prompt != null) { putString("routeSuggestion", prompt); remove("routeSuggestionAccepted") }
            if (accepted != null) putBoolean("routeSuggestionAccepted", accepted)
        }.apply()
        if (prompt != null) { routeSuggestion = prompt; routeSuggestionAccepted = null }
        if (accepted != null) routeSuggestionAccepted = accepted
    }

    /** Remembered drives, newest first (kept on the device, at most 20). */
    var driveJournal by mutableStateOf(loadDriveJournal())
        private set

    private fun loadDriveJournal(): List<DriveJournalEntry> {
        val entries = runCatching { JSONArray(prefs.getString("driveJournal", "[]")) }.getOrNull() ?: return emptyList()
        return (0 until entries.length()).mapNotNull { index ->
            val e = entries.optJSONObject(index) ?: return@mapNotNull null
            DriveJournalEntry(
                runCatching { LocalDate.parse(e.optString("day")) }.getOrNull() ?: return@mapNotNull null,
                e.optString("mode"),
                e.optString("place").ifBlank { null },
                e.optDouble("km").takeIf { !it.isNaN() }?.toFloat(),
                e.optDouble("eff").takeIf { !it.isNaN() },
                e.optString("mood").ifBlank { null },
                e.optDouble("lat").takeIf { !it.isNaN() },
                e.optDouble("lon").takeIf { !it.isNaN() },
            )
        }
    }

    private fun saveDriveJournal(list: List<DriveJournalEntry>) {
        val array = JSONArray()
        list.take(20).forEach { entry ->
            array.put(JSONObject().apply {
                put("day", entry.day.toString()); put("mode", entry.mode)
                entry.place?.let { put("place", it) }; entry.distanceKm?.let { put("km", it.toDouble()) }
                entry.efficiency?.let { put("eff", it) }; entry.mood?.let { put("mood", it) }
                entry.latitude?.let { put("lat", it) }; entry.longitude?.let { put("lon", it) }
            })
        }
        prefs.edit().putString("driveJournal", array.toString()).apply()
        driveJournal = list.take(20)
    }

    private fun addJournalEntry(entry: DriveJournalEntry) = saveDriveJournal(listOf(entry) + driveJournal)

    private fun updateLatestJournal(entry: DriveJournalEntry) =
        saveDriveJournal(listOf(entry) + driveJournal.drop(1))

    /**
     * After arrival (parked), the driver can say what the drive was really for, instead of having
     * to pick a mode before or during the drive. Keeps the report, journal and acceptance data in sync.
     */
    fun correctDriveMode(mode: String) {
        val previous = lastMission ?: return
        if (previous == mode) return
        val record = lastRecommendation?.takeIf { it.chosen == previous }
        val wasAccepted = record?.accepted == true
        val nowAccepted = record?.recommended == mode
        val accepted = recommendationAccepted + when {
            record == null -> 0
            wasAccepted && !nowAccepted -> -1
            !wasAccepted && nowAccepted -> 1
            else -> 0
        }
        prefs.edit().putString("lastMission", mode).putInt("recoAccepted", accepted).apply {
            if (record != null) putString("recoChosen", mode)
            if (lastTrip?.mode == previous) putString("tripReportMode", mode)
        }.apply()
        lastMission = mode
        recommendationAccepted = accepted
        if (record != null) lastRecommendation = record.copy(chosen = mode)
        lastTrip?.takeIf { it.mode == previous }?.let { lastTrip = it.copy(mode = mode) }
        driveJournal.firstOrNull()?.takeIf { it.mode == previous }?.let { updateLatestJournal(it.copy(mode = mode)) }
    }

    fun saveDriveMood(mood: String) {
        driveJournal.firstOrNull()?.let { updateLatestJournal(it.copy(mood = mood)) }
    }

    fun saveDriveFeedback(feedback: String) {
        prefs.edit().putString("lastFeedback", feedback).apply()
        lastFeedback = feedback
    }

    /** The X-LAB experiment whose question this drive's report asks (one per drive, in rotation). */
    var tripExperimentId by mutableStateOf(prefs.getString("tripExperiment", null))
        private set
    var tripExperimentAnswer by mutableStateOf(prefs.getString("tripExperimentAnswer", null))
        private set
    /** Whether the question was already offered as a notification over navigation. */
    var tripExperimentPrompted = prefs.getBoolean("tripExperimentPrompted", false)
        private set

    fun markExperimentPrompted() {
        tripExperimentPrompted = true
        prefs.edit().putBoolean("tripExperimentPrompted", true).apply()
    }
    /** Drive-report answers per experiment id, kept on the device. */
    var experimentResponses by mutableStateOf(loadExperimentResponses())
        private set

    private fun loadExperimentResponses(): Map<String, Int> {
        val json = runCatching { JSONObject(prefs.getString("experimentResponses", "{}") ?: "{}") }.getOrNull()
            ?: return emptyMap()
        return json.keys().asSequence().associateWith { json.optInt(it) }
    }

    /** Picks the next experiment question for the report of the drive that just ended. */
    /**
     * Picks the one experiment question for this drive's report. When navigation predicted the arrival
     * battery, the report shows predicted vs actual, so the question asks about that gap; otherwise the
     * remaining experiments take turns (the arrival-battery one is skipped, there is nothing to ask about).
     */
    private fun nextTripExperiment(): String {
        val experiment = if (prefs.contains("tripPredictKwh")) {
            LabExperiments.first { it.id == ArrivalBatteryExperiment }
        } else {
            val pool = LabExperiments.filter { it.id != ArrivalBatteryExperiment }
            val index = prefs.getInt("experimentRotation", 0)
            prefs.edit().putInt("experimentRotation", index + 1).apply()
            pool[index.mod(pool.size)]
        }
        prefs.edit().putString("tripExperiment", experiment.id)
            .remove("tripExperimentAnswer").remove("tripExperimentPrompted").apply()
        tripExperimentId = experiment.id
        tripExperimentAnswer = null
        tripExperimentPrompted = false
        return experiment.id
    }

    /** The arrival-battery question only makes sense once the report has both numbers to compare. */
    val tripExperimentAskable: Boolean
        get() = tripExperimentId != ArrivalBatteryExperiment ||
            (lastTrip?.arrivalGapKwh?.let { kotlin.math.abs(it) >= ArrivalGapNoticeKwh } == true)

    fun saveExperimentAnswer(answer: String) {
        val id = tripExperimentId ?: return
        if (tripExperimentAnswer != null) return
        val counts = experimentResponses + (id to (experimentResponses[id] ?: 0) + 1)
        prefs.edit().putString("tripExperimentAnswer", answer)
            .putString("experimentResponses", JSONObject(counts).toString()).apply()
        tripExperimentAnswer = answer
        experimentResponses = counts
        ExperimentPrompt.cancel(context)
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
            .putString("missionDestinationLat", target?.latitude?.toString())
            .putString("missionDestinationLon", target?.longitude?.toString())
            .apply()
        linkedMissionDestination = target
    }

    fun finishMission(feedback: String?, destination: LinkedMissionDestination? = null): MissionCompletion? {
        val intent = activeMission ?: return null
        if (destination != null && destination != linkedMissionDestination) return null
        val linkedBefore = linkedMissionDestination
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
        addJournalEntry(DriveJournalEntry(
            today, intent, destinationLabel ?: linkedBefore?.label?.ifBlank { null }, null, null, null,
            destination?.latitude ?: linkedBefore?.latitude,
            destination?.longitude ?: linkedBefore?.longitude,
        ))
        prefs.edit().putString("tripReportMode", intent).putBoolean("tripReportPending", true)
            .remove("tripReportKm").remove("tripReportKwh").remove("tripReportCharged")
            .remove("tripReportPredict").remove("tripReportActual").apply()
        lastTrip = TripReport(intent, null, null, charged = false, pending = true)
        nextTripExperiment()
        freshVehicleSample()?.let(::finishTripReport)
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
        // Older in-app trials (timed taps, other tasks) are not comparable with the real-car SYNC A/B.
        val task = ClimateTask.entries.firstOrNull { it.name == prefs.getString("comfortTask", null) } ?: ClimateTask.SyncPreset
        if (task != ClimateTask.SyncPreset || prefs.getInt("comfortBaselineTouches", 0) >= 0) return null
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
            task = task,
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
