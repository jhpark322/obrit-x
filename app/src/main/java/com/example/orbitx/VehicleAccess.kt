package com.example.orbitx

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.round

internal enum class ParkingStatus(val label: String) {
    Parked("주차 · P"), Restricted("주행 제한"), Unavailable("차량 상태 확인 중")
}

internal enum class CabinSeat(val label: String, val value: SeatValue) {
    Driver("운전석", SeatValue.ROW_1_LEFT),
    Passenger("조수석", SeatValue.ROW_1_RIGHT),
    RearLeft("뒷좌석 왼쪽", SeatValue.ROW_2_LEFT),
    RearRight("뒷좌석 오른쪽", SeatValue.ROW_2_RIGHT),
}

internal data class CabinClimateZone(val seats: Set<SeatValue>, val powered: Boolean?)
internal data class CabinZoneReading<T>(val seats: Set<SeatValue>, val value: T?)
internal data class SeatComfort(val heating: Boolean, val level: Int)
internal const val CabinTargetCelsius = 23f

internal fun seatComfortTarget(cabin: Float): SeatComfort? {
    if (!cabin.isFinite()) return null
    val difference = CabinTargetCelsius - cabin
    if (abs(difference) < 2.5f) return null
    return SeatComfort(difference > 0, if (abs(difference) >= 5.5f) 2 else 1)
}

internal data class CabinClimateSnapshot(
    val occupied: Map<CabinSeat, Boolean?>,
    val zones: List<CabinClimateZone>,
    val fanSpeeds: List<CabinZoneReading<Int>>,
    val temperatures: List<CabinZoneReading<Float>>,
    val measuredTemperatures: List<CabinZoneReading<Float>>,
    val ecoSeats: Set<SeatValue>,
    val seatComfort: Map<CabinSeat, SeatComfort>,
    val coolingOn: Boolean?,
    val status: String,
    val occupiedZonePowered: Boolean,
) {
    private fun seatsIn(zone: CabinClimateZone) = CabinSeat.entries.filter { it.value in zone.seats }
    private fun emptyZone(zone: CabinClimateZone) = seatsIn(zone).let { seats ->
        seats.isNotEmpty() && seats.all { occupied[it] == false }
    }

    /** Climate zones powered on although every seat they serve is empty: energy spent on nobody. */
    val idleZones: List<CabinClimateZone> get() = zones.filter { it.powered == true && emptyZone(it) }
    /** Climate zones kept off because their seats are empty. */
    val savingZones: List<CabinClimateZone> get() = zones.filter { it.powered == false && emptyZone(it) }
    fun seatLabels(zones: List<CabinClimateZone>) = zones.flatMap(::seatsIn).distinct().joinToString(", ") { it.label }
}

internal data class EfficiencyReading(val value: Float, val period: String)
internal data class DrivingTelemetry(
    val odometerKm: Float?,
    val evEfficiency: EfficiencyReading?,
    val fuelEconomy: EfficiencyReading?,
    val batteryPercent: Int?,
    val fuelPercent: Int?,
)
internal data class LeagueVehicleSample(
    val odometerKm: Float?,
    val batteryKwh: Float?,
    val fuelLiters: Float? = null,
    val chargeSwitchOn: Boolean? = null,
)

/** AAOS runtime permission for PERF_VEHICLE_SPEED. */
internal const val SpeedPermission = "android.car.permission.CAR_SPEED"

internal class VehicleAccess(private val context: Context, private val scope: CoroutineScope) {
    private var gear by mutableStateOf<VehicleGear?>(null)
    private var requiresOptimization by mutableStateOf<Boolean?>(null)
    private var car: Car? = null
    private var restrictions: CarUxRestrictionsManager? = null
    @Volatile private var properties: CarPropertyManager? = null
    private var sdk: Vehicle? = null
    private var readJob: Job? = null
    private var connection = 0
    // This AVD image reports Empty as true and Occupied as false through Vehicle SDK 2.3.2.
    private val invertedSeatReading = Build.DEVICE == "crp_avd_x86_64" &&
        Build.FINGERPRINT.endsWith("release_connect_v2.1.0:user/test-keys")
    val usesPleosDemoProfile = Build.DEVICE == "crp_avd_x86_64" && Build.FINGERPRINT.startsWith("Pleos/")

    val parking by derivedStateOf {
        when {
            requiresOptimization == true -> ParkingStatus.Restricted
            gear == null || gear == VehicleGear.GEAR_UNKNOWN -> ParkingStatus.Unavailable
            gear != VehicleGear.GEAR_PARK -> ParkingStatus.Restricted
            requiresOptimization == null -> ParkingStatus.Unavailable
            else -> ParkingStatus.Parked
        }
    }

    fun start() {
        val currentConnection = ++connection
        car = runCatching {
            Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { service, ready ->
                if (connection == currentConnection) attachRestrictions(service, ready)
            }
        }.onFailure { Log.w(TAG, "Unable to connect to AAOS", it) }.getOrNull()
        val vehicle = Vehicle(context)
        sdk = vehicle
        runCatching { vehicle.initialize() }
            .onFailure { Log.w(TAG, "Unable to initialize Vehicle SDK", it) }
        readJob = scope.launch {
            while (isActive) {
                gear = withTimeoutOrNull(1_250) { readGear(vehicle) }
                delay(450)
            }
        }
    }

    private fun attachRestrictions(service: Car, ready: Boolean) {
        clearRestrictions()
        properties = null
        if (!ready) return
        properties = runCatching { service.getCarManager(CarPropertyManager::class.java) }.getOrNull()
        runCatching {
            val manager = service.getCarManager(CarUxRestrictionsManager::class.java) ?: return
            restrictions = manager
            manager.registerListener { restrictions ->
                if (this.restrictions === manager) {
                    requiresOptimization = restrictions.isRequiresDistractionOptimization
                }
            }
            requiresOptimization = manager.currentCarUxRestrictions?.isRequiresDistractionOptimization
        }.onFailure { Log.w(TAG, "Unable to observe driving restrictions", it) }
    }

    private suspend fun readGear(vehicle: Vehicle): VehicleGear? = suspendCancellableCoroutine { result ->
        runCatching {
            vehicle.getGear().getCurrentGearSelectionState(
                onComplete = { value -> if (result.isActive) result.resume(value) },
                onFailed = { error ->
                    Log.d(TAG, "Gear selection unavailable: ${error.message}")
                    if (result.isActive) result.resume(null)
                },
            )
        }.onFailure { error ->
            Log.d(TAG, "Gear selection unavailable: ${error.message}")
            if (result.isActive) result.resume(null)
        }
    }

    suspend fun readFrontSync(): Boolean? = sdk?.let { readFrontSync(it) }

    /** Vehicle speed in km/h from AAOS (Pleos SDK has none); needs the runtime CAR_SPEED permission. */
    suspend fun readSpeedKph(): Float? {
        if (context.checkSelfPermission(SpeedPermission) != PackageManager.PERMISSION_GRANTED) return null
        // AAOS reports m/s.
        return readCarProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED)
            ?.takeIf { it.isFinite() }?.let { abs(it) * 3.6f }
    }

    /** Whether the brake pedal is pressed (Pleos Vehicle SDK). */
    suspend fun readBrakePressed(): Boolean? {
        val vehicle = sdk ?: return null
        return readVehicleValue(vehicle.getBrake()::getCurrentBrakePedalStatus)
    }

    private var climatePowerAreas: List<SeatArea>? = null

    /** Whether any climate zone is powered, read-only. */
    suspend fun readClimateOn(): Boolean? {
        val vehicle = sdk ?: return null
        val hvac = vehicle.getHVAC()
        val areas = climatePowerAreas ?: readVehicleValue<List<SeatArea>> { done, failed ->
            hvac.checkHvacPowerCapabilities({ _, supported -> done(supported) }, failed)
        }?.also { climatePowerAreas = it }
        if (areas.isNullOrEmpty()) return null
        val states = areas.map { area -> readVehicleValue<Boolean> { done, failed -> hvac.getHvacPowerState(area, done, failed) } }
        return if (states.all { it == null }) null else states.any { it == true }
    }

    suspend fun readDrivingTelemetry(): DrivingTelemetry? {
        val vehicle = sdk ?: return null
        return coroutineScope {
            val odometer = async { readVehicleValue(vehicle.getOdometer()::getCurrentDistanceDriven) }
            val battery = vehicle.getEvBattery()
            val fuelSystem = vehicle.getFuel()
            val batteryPercent = async { readPercent(battery::getEvBatteryStateOfCharge) }
            val fuelPercent = async { readPercent(fuelSystem::getFuelPercent) }
            val ev = async {
                readEfficiency("현재", battery::getCurrentEvEfficiency)
                    ?: readEfficiency("누적", battery::getCumulativeEvEfficiency)
                    ?: readEfficiency("충전 후", battery::getSinceChargeEvEfficiency)
            }
            val fuel = async {
                readEfficiency("현재", fuelSystem::getCurrentFuelEconomy)
                    ?: readEfficiency("누적", fuelSystem::getCumulativeFuelEconomy)
                    ?: readEfficiency("주유 후", fuelSystem::getSinceChargeFuelEconomy)
            }
            DrivingTelemetry(
                odometer.await()?.takeIf { it.isFinite() && it >= 0 },
                ev.await(),
                fuel.await(),
                batteryPercent.await(),
                fuelPercent.await(),
            )
        }
    }

    private suspend fun readEfficiency(
        period: String,
        request: ((Float?) -> Unit, (Exception) -> Unit) -> Unit,
    ): EfficiencyReading? = readVehicleValue(request)
        ?.takeIf { it.isFinite() && it > 0 }?.let { EfficiencyReading(it, period) }

    suspend fun readBatteryPercent(): Int? {
        val vehicle = sdk ?: return null
        return readPercent(vehicle.getEvBattery()::getEvBatteryStateOfCharge)
    }

    private suspend fun readPercent(
        request: ((Int?) -> Unit, (Exception) -> Unit) -> Unit,
    ): Int? = readVehicleValue(request)?.takeIf { it in 0..100 }

    suspend fun readLeagueSample(): LeagueVehicleSample? {
        val vehicle = sdk ?: return null
        return coroutineScope {
            val odometer = async { readVehicleValue(vehicle.getOdometer()::getCurrentDistanceDriven) }
            val evBattery = vehicle.getEvBattery()
            val battery = async { readVehicleValue(evBattery::getEvBatteryLevel) }
            val chargeSwitch = async { readVehicleValue(evBattery::getEvChargeSwitchState) }
            val fuel = async {
                if (context.checkSelfPermission(Car.PERMISSION_ENERGY) == PackageManager.PERMISSION_GRANTED) {
                    readCarProperty<Float>(VehiclePropertyIds.FUEL_LEVEL)
                } else null
            }
            val km = odometer.await()
            val wh = battery.await()
            if (sdk !== vehicle) return@coroutineScope null
            LeagueVehicleSample(
                km?.takeIf { it.isFinite() && it >= 0f },
                wh?.takeIf { it.isFinite() && it >= 0f }?.div(1_000f),
                fuel.await()?.takeIf { it.isFinite() && it >= 0f }?.div(1_000f),
                chargeSwitch.await(),
            )
        }
    }

    suspend fun readLeagueModel(): String? = readCarProperty<String>(VehiclePropertyIds.INFO_MODEL)

    /** True when all four passenger doors report locked, false if any is unlocked, null if unreadable. */
    suspend fun readDoorsLocked(): Boolean? {
        val vehicle = sdk ?: return null
        if (context.checkSelfPermission(DOOR_READ) != PackageManager.PERMISSION_GRANTED) return null
        // Many HALs (including Pleos Connect 2.1.0) expose DOOR_LOCK only as one combined area for all four doors.
        readVehicleValue<Boolean> { done, failed -> vehicle.getDoor().getDoorLockState(AllPassengerDoors, done, failed) }
            ?.let { return it }
        val states = PassengerDoors.map { door ->
            readVehicleValue<Boolean> { done, failed -> vehicle.getDoor().getDoorLockState(DoorArea(door), done, failed) }
        }
        return when {
            states.any { it == false } -> false
            states.all { it == true } -> true
            else -> null
        }
    }

    /**
     * Locks or unlocks all passenger doors through the Vehicle SDK, only while parked (gear P),
     * then reads the doors back so the screen shows the vehicle's state rather than the request.
     */
    suspend fun setDoorsLocked(lock: Boolean): Boolean? {
        val vehicle = sdk ?: return null
        if (context.checkSelfPermission(DOOR_CONTROL) != PackageManager.PERMISSION_GRANTED) return null
        if (!canWriteClimate(vehicle)) return null
        val combined = climateCommand { done, failed ->
            vehicle.getDoor().setDoorLock(AllPassengerDoors, lock, { _, _ -> done() }, failed)
        }
        if (!combined) PassengerDoors.forEach { door ->
            climateCommand { done, failed ->
                vehicle.getDoor().setDoorLock(DoorArea(door), lock, { _, _ -> done() }, failed)
            }
        }
        delay(250)
        return readDoorsLocked()
    }

    /** Measured cabin temperature (°C) from the HVAC, trying the area layouts vehicles commonly expose. */
    suspend fun readCabinTemperatureC(): Float? {
        val vehicle = sdk ?: return null
        val hvac = vehicle.getHVAC()
        val areas = listOf(
            SeatArea(SeatValue.ROW_1_LEFT),
            FrontSeats,
            SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT),
        )
        for (area in areas) {
            readVehicleValue<Float> { done, failed ->
                hvac.getHvacTemperatureCurrent(area, TemperatureUnit.CELSIUS, done, failed)
            }?.takeIf { it.isFinite() && it in -30f..65f }?.let { return it }
        }
        return null
    }

    /** Vehicle-estimated remaining range in km (the SDK reports meters). */
    suspend fun readRangeKm(): Float? {
        val vehicle = sdk ?: return null
        return readVehicleValue(vehicle.getOdometer()::getRangeRemainingDistance)
            ?.takeIf { it.isFinite() && it > 0f }?.div(1_000f)
    }

    private suspend inline fun <reified T : Any> readCarProperty(property: Int): T? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                properties?.getProperty(T::class.javaObjectType, property, 0)
                    ?.takeIf { it.status == CarPropertyValue.STATUS_AVAILABLE }?.value
            }.getOrNull()
        }

    private suspend fun <T> readVehicleValue(
        request: ((T?) -> Unit, (Exception) -> Unit) -> Unit,
    ): T? = withTimeoutOrNull(1_500) {
        suspendCancellableCoroutine { continuation ->
            val failure: (Exception) -> Unit = { error ->
                Log.d(TAG, "Vehicle reading unavailable", error)
                if (continuation.isActive) continuation.resume(null)
            }
            try {
                request({ value -> if (continuation.isActive) continuation.resume(value) }, failure)
            } catch (error: Exception) {
                failure(error)
            }
        }
    }

    suspend fun observeCabinClimate(automatic: Boolean, onUpdate: (CabinClimateSnapshot) -> Unit) {
        val vehicle = sdk ?: return
        val hvac = vehicle.getHVAC()
        val canControl = context.checkSelfPermission(CLIMATE_CONTROL) == PackageManager.PERMISSION_GRANTED
        var areas: List<SeatArea>? = if (canControl) null else emptyList()
        var previousOccupancy = emptyMap<CabinSeat, Boolean?>()
        val powerAttempts = mutableMapOf<SeatArea, Pair<Boolean, Long>>()
        val failedZones = mutableSetOf<SeatArea>()
        var fanAreas: Map<SeatArea, AreaBounds<Int>>? = if (canControl) null else emptyMap()
        var temperatureAreas: Map<SeatArea, AreaBounds<Float>>? = if (canControl) null else emptyMap()
        var temperatureStep = 0f
        var acAreas: List<SeatArea>? = if (canControl) null else emptyList()
        val canControlSeats = context.checkSelfPermission(SEAT_CONTROL) == PackageManager.PERMISSION_GRANTED
        var seatTemperatureAreas: Map<SeatArea, AreaBounds<Int>>? = if (canControlSeats) null else emptyMap()
        var seatVentilationAreas: Map<SeatArea, AreaBounds<Int>>? = if (canControlSeats) null else emptyMap()
        val seatComfortEvaluated = mutableSetOf<CabinSeat>()
        val emptySeatComfortAttempts = mutableMapOf<CabinSeat, Long>()
        val coolingAttempts = mutableMapOf<SeatArea, Pair<Boolean, Long>>()
        val fanAttempts = mutableMapOf<SeatArea, Pair<Boolean, Long>>()
        val lastTemperatureTarget = mutableMapOf<SeatArea, Float>()
        val ecoTemperatureTargets = mutableMapOf<SeatArea, Float>()
        val emptyTemperatureApplied = mutableSetOf<SeatArea>()
        while (parking == ParkingStatus.Parked && sdk === vehicle) {
            val occupied = coroutineScope {
                CabinSeat.entries.associateWith { seat -> async { readSeatOccupied(vehicle, seat.value) } }
                    .mapValues { (_, result) -> result.await() }
            }
            if (areas == null) {
                areas = readVehicleValue<List<SeatArea>> { done, failed ->
                    hvac.checkHvacPowerCapabilities(
                        { config, supported -> done(if (config.accessType == AccessType.READ_WRITE) supported else emptyList()) },
                        failed,
                    )
                }
                Log.i(TAG, "Cabin climate power areas: ${areas?.map { it.values.toList() }}")
            }
            if (fanAreas == null) {
                fanAreas = readVehicleValue<Map<SeatArea, AreaBounds<Int>>> { done, failed ->
                    hvac.checkHvacFanSpeedCapabilities(
                        { config, supported -> done(if (config.accessType == AccessType.READ_WRITE) supported else emptyMap()) },
                        failed,
                    )
                }
                Log.i(TAG, "Cabin fan speed areas: ${fanAreas?.map { (area, bounds) -> area.values.toList() to (bounds.minValue to bounds.maxValue) }}")
            }
            if (temperatureAreas == null) {
                temperatureAreas = readVehicleValue<Map<SeatArea, AreaBounds<Float>>> { done, failed ->
                    hvac.checkHvacTemperatureCapabilities(
                        { config, supported, temperature ->
                            temperatureStep = temperature?.incrementCelsius ?: 0f
                            done(if (config.accessType == AccessType.READ_WRITE) supported else emptyMap())
                        },
                        failed,
                    )
                }
            }
            if (acAreas == null) {
                acAreas = readVehicleValue<List<SeatArea>> { done, failed ->
                    hvac.checkHvacAcCapabilities(
                        { config, supported -> done(if (config.accessType == AccessType.READ_WRITE) supported else emptyList()) },
                        failed,
                    )
                }
            }
            if (seatTemperatureAreas == null) {
                seatTemperatureAreas = readVehicleValue<Map<SeatArea, AreaBounds<Int>>> { done, failed ->
                    vehicle.getSeat().checkSeatTemperatureCapabilities(
                        { config, supported -> done(if (config.accessType == AccessType.READ_WRITE) supported else emptyMap()) },
                        failed,
                    )
                }
            }
            if (seatVentilationAreas == null) {
                seatVentilationAreas = readVehicleValue<Map<SeatArea, AreaBounds<Int>>> { done, failed ->
                    vehicle.getSeat().checkSeatVentilationCapabilities(
                        { config, supported -> done(if (config.accessType == AccessType.READ_WRITE) supported else emptyMap()) },
                        failed,
                    )
                }
            }
            val stable = if (automatic) {
                occupied.filter { (seat, value) -> value != null && value == previousOccupancy[seat] }
            } else emptyMap()
            val now = SystemClock.elapsedRealtime()
            val cabinVacant = CabinSeat.entries.all { stable[it] == false }
            val someoneSeated = stable.values.any { it == true }
            val zones = areas.orEmpty().map { area ->
                val values = area.values.toSet()
                val seats = CabinSeat.entries.filter { it.value in values }
                val demand = when {
                    seats.any { stable[it] == true } -> true
                    seats.isNotEmpty() && seats.all { stable[it] == false } -> false
                    else -> null
                }
                var power = readVehicleValue<Boolean> { done, failed -> hvac.getHvacPowerState(area, done, failed) }
                if (demand != null && power != null) {
                    val priorAttempt = powerAttempts[area]
                    if (demand == power) {
                        powerAttempts.remove(area)
                        failedZones.remove(area)
                    } else if ((priorAttempt?.first != demand || now - priorAttempt.second >= 3_000L) &&
                        canWriteClimate(vehicle)
                    ) {
                        powerAttempts[area] = demand to now
                        if (cabinVacant && !demand) {
                            for (coolingArea in acAreas.orEmpty().filter { values.containsAll(it.values.toList()) }) {
                                val ac = readVehicleValue<Boolean> { done, failed ->
                                    hvac.getHvacAcState(coolingArea, done, failed)
                                }
                                if (ac == true && canWriteClimate(vehicle)) {
                                    climateCommand { done, failed -> hvac.setHvacAc(coolingArea, false, { _, _ -> done() }, failed) }
                                }
                            }
                            for ((fanArea, bounds) in fanAreas.orEmpty()) {
                                if (bounds.minValue == 0 && values.containsAll(fanArea.values.toList()) &&
                                    canWriteClimate(vehicle)
                                ) {
                                    climateCommand { done, failed -> hvac.setHvacFanSpeed(fanArea, 0, { _, _ -> done() }, failed) }
                                }
                            }
                        }
                        if (!canWriteClimate(vehicle)) return@map CabinClimateZone(values, power)
                        val accepted = climateCommand { done, failed ->
                            hvac.setHvacPower(area, demand, { _, _ -> done() }, failed)
                        }
                        if (accepted) {
                            delay(250)
                            power = readVehicleValue<Boolean> { done, failed -> hvac.getHvacPowerState(area, done, failed) }
                        }
                        if (accepted && power == demand) {
                            failedZones.remove(area)
                        } else {
                            failedZones.add(area)
                        }
                    }
                }
                CabinClimateZone(values, power)
            }
            val coolingStates = acAreas.orEmpty().associateWith { area ->
                readVehicleValue<Boolean> { done, failed -> hvac.getHvacAcState(area, done, failed) }
            }.toMutableMap()
            for ((area, ac) in coolingStates) {
                val power = zones.firstOrNull { it.seats.containsAll(area.values.toList()) }?.powered
                if (power == false) {
                    coolingStates[area] = false
                    coolingAttempts.remove(area)
                    continue
                }
                val demand = when {
                    cabinVacant -> false
                    someoneSeated -> true
                    else -> continue
                }
                val priorAttempt = coolingAttempts[area]
                if (ac == demand) {
                    coolingAttempts.remove(area)
                } else if (ac != null && (priorAttempt?.first != demand || now - priorAttempt.second >= 3_000L) &&
                    canWriteClimate(vehicle)
                ) {
                    coolingAttempts[area] = demand to now
                    if (climateCommand { done, failed -> hvac.setHvacAc(area, demand, { _, _ -> done() }, failed) }) {
                        delay(250)
                        coolingStates[area] = readVehicleValue<Boolean> { done, failed -> hvac.getHvacAcState(area, done, failed) }
                    }
                }
            }
            val coolingOn = when {
                zones.isNotEmpty() && zones.all { it.powered == false } -> false
                coolingStates.values.any { it == true } -> true
                coolingStates.isNotEmpty() && coolingStates.values.all { it == false } -> false
                else -> null
            }
            val fanSpeeds = fanAreas.orEmpty().map { (area, bounds) ->
                val values = area.values.toSet()
                val seats = CabinSeat.entries.filter { it.value in values }
                val demand = when {
                    seats.any { stable[it] == true } -> true
                    cabinVacant -> false
                    seats.isNotEmpty() && seats.size == values.size && seats.all { stable[it] == false } -> false
                    else -> null
                }
                val read = suspend { readVehicleValue<Int> { done, failed -> hvac.getHvacFanSpeed(area, done, failed) } }
                var speed = read()
                if (demand != null && speed != null &&
                    zones.any { it.powered == true && it.seats.containsAll(values) }
                ) {
                    val low = bounds.minValue
                    val high = bounds.maxValue
                    if (low != null && high != null && low <= high) {
                        val target = if (demand) maxOf(speed, (low + 2).coerceAtMost(high)) else low
                        val priorAttempt = fanAttempts[area]
                        if (target == speed) {
                            fanAttempts.remove(area)
                        } else if ((priorAttempt?.first != demand || now - priorAttempt.second >= 3_000L) &&
                            canWriteClimate(vehicle)
                        ) {
                            fanAttempts[area] = demand to now
                            if (climateCommand { done, failed -> hvac.setHvacFanSpeed(area, target, { _, _ -> done() }, failed) }) {
                                speed = read()
                            }
                        }
                    }
                }
                CabinZoneReading(values, speed)
            }
            val temperatures = temperatureAreas.orEmpty().keys.associateWith { readCabinTemperature(vehicle, it) }.toMutableMap()
            val measured = coroutineScope {
                temperatureAreas.orEmpty().keys.associateWith { area -> async {
                    readVehicleValue<Float> { done, failed ->
                        hvac.getHvacTemperatureCurrent(area, TemperatureUnit.CELSIUS, done, failed)
                    }?.takeIf { it.isFinite() && it in -30f..65f }
                } }.mapValues { (_, result) -> result.await() }
            }
            val ecoSeats = mutableSetOf<SeatValue>()
            for ((area, bounds) in temperatureAreas.orEmpty()) {
                val seats = CabinSeat.entries.filter { it.value in area.values }
                val current = temperatures[area] ?: continue
                val low = bounds.minValue ?: continue
                val high = bounds.maxValue ?: continue
                if (low > high) continue
                val stored = ecoTemperatureTargets[area]
                if (stored != null && abs(current - stored) >= .25f) {
                    ecoTemperatureTargets.remove(area)
                }
                val occupiedZone = seats.any { stable[it] == true }
                val powered = zones.any { it.powered == true && it.seats.containsAll(area.values.toList()) }
                if (occupiedZone) {
                    emptyTemperatureApplied.remove(area)
                    ecoTemperatureTargets.remove(area)
                    val target = CabinTargetCelsius.coerceIn(low, high)
                    if (powered && target != lastTemperatureTarget[area]) {
                        lastTemperatureTarget[area] = target
                        if (abs(current - target) >= .25f) {
                            updateCabinTemperature(vehicle, area, target)?.let { temperatures[area] = it }
                        }
                    }
                } else if (seats.isNotEmpty() && seats.size == area.values.size && seats.all { stable[it] == false }) {
                    lastTemperatureTarget.remove(area)
                    if (!powered || cabinVacant) continue
                    val actual = measured[area]
                    val fan = fanSpeeds.firstOrNull { it.seats.containsAll(area.values.toList()) }
                    val front = area.values.any { it == CabinSeat.Driver.value || it == CabinSeat.Passenger.value }
                    if (area !in emptyTemperatureApplied && actual != null && fan?.value == 1 &&
                        fan.seats.all { value -> seatsForValue(value)?.let { stable[it] } == false } &&
                        temperatureStep.isFinite() && temperatureStep > 0f &&
                        (!front || readFrontSync(vehicle) == false)
                    ) {
                        val target = (low + round((actual.coerceIn(low, high) - low) / temperatureStep) * temperatureStep)
                            .coerceIn(low, high)
                        emptyTemperatureApplied.add(area)
                        if (abs(current - target) >= 1f) {
                            val adjusted = updateCabinTemperature(vehicle, area, target)
                            if (adjusted != null) {
                                temperatures[area] = adjusted
                                if (abs(adjusted - target) < .25f) {
                                    ecoTemperatureTargets[area] = adjusted
                                }
                            }
                        }
                    }
                    if (area in ecoTemperatureTargets) ecoSeats.addAll(area.values)
                }
            }
            val cabinAverage = measured.values.filterNotNull().average().toFloat().takeIf(Float::isFinite)
            val comfort = CabinSeat.entries.mapNotNull { seat ->
                val area = SeatArea(seat.value)
                val localCabin = measured.entries.firstOrNull { seat.value in it.key.values }?.value ?: cabinAverage
                val target = localCabin?.let(::seatComfortTarget)
                val reading = applySeatComfort(
                    vehicle, seat, stable[seat], target, localCabin != null,
                    seatTemperatureAreas?.get(area), seatVentilationAreas?.get(area), seatComfortEvaluated,
                    emptySeatComfortAttempts,
                )
                reading?.let { seat to it }
            }.toMap()
            previousOccupancy = occupied
            val status = when {
                !automatic -> "자동 제어 꺼짐 · 차량 상태만 표시해요."
                !canControl -> "공조 제어 권한을 사용할 수 없어요."
                areas == null -> "지원하는 공조 구역을 확인하고 있어요."
                zones.isEmpty() -> "이 차량은 앱에서 공조 전원을 제어할 수 없어요."
                cabinVacant && failedZones.isNotEmpty() -> "빈 차량 · 공조 끄기 재시도 중"
                failedZones.isNotEmpty() -> "일부 공조 변경을 차량에서 확인하지 못했어요."
                zones.all { it.powered == null } -> "순정 공조 전원 상태를 확인하고 있어요."
                cabinVacant && comfort.isNotEmpty() -> "빈 차량 · 열선·통풍 끄는 중"
                cabinVacant && zones.all { it.powered == false } -> "빈 차량 · 공조 꺼짐"
                cabinVacant && coolingOn == false -> "빈 차량 · A/C 꺼짐"
                cabinVacant -> "빈 차량 · 공조 끄는 중"
                someoneSeated && zones.any { it.powered == true } -> "탑승 감지 · 공조 켜짐"
                someoneSeated -> "탑승 감지 · 공조 켜는 중"
                else -> "착석 상태 확인 중"
            }
            onUpdate(CabinClimateSnapshot(
                occupied, zones, fanSpeeds,
                temperatures.map { (area, value) -> CabinZoneReading(area.values.toSet(), value) },
                measured.map { (area, value) -> CabinZoneReading(area.values.toSet(), value) },
                ecoSeats,
                comfort,
                coolingOn,
                status,
                zones.any { zone ->
                    zone.powered == true && stable.any { (seat, seated) -> seated == true && seat.value in zone.seats }
                },
            ))
            delay(750)
        }
    }

    private suspend fun applySeatComfort(
        vehicle: Vehicle,
        seat: CabinSeat,
        occupied: Boolean?,
        target: SeatComfort?,
        hasTemperatures: Boolean,
        heatBounds: AreaBounds<Int>?,
        ventBounds: AreaBounds<Int>?,
        evaluated: MutableSet<CabinSeat>,
        emptyAttempts: MutableMap<CabinSeat, Long>,
    ): SeatComfort? {
        if (heatBounds == null && ventBounds == null) return null
        val area = SeatArea(seat.value)
        val sdkSeat = vehicle.getSeat()
        val readHeat = suspend { readVehicleValue<Int> { done, failed -> sdkSeat.getHvacSeatTemperature(area, done, failed) } }
        val readVent = suspend { readVehicleValue<Int> { done, failed -> sdkSeat.getHvacSeatVentilationState(area, done, failed) } }
        var heat = if (heatBounds == null) 0 else readHeat()
        var vent = if (ventBounds == null) 0 else readVent()
        fun current(): SeatComfort? {
            val seatTemperature = heat ?: 0
            val seatVentilation = vent ?: 0
            return when {
                seatTemperature > 0 -> SeatComfort(true, seatTemperature)
                seatVentilation > 0 -> SeatComfort(false, seatVentilation)
                seatTemperature < 0 -> SeatComfort(false, -seatTemperature)
                else -> null
            }
        }
        val existing = current()
        if (occupied == false) evaluated.remove(seat)
        if (occupied == true || existing == null) emptyAttempts.remove(seat)
        val now = SystemClock.elapsedRealtime()
        val retryDue = emptyAttempts[seat]?.let { now - it >= 3_000L } ?: true
        val turningOff = occupied == false && existing != null && retryDue
        val turningOn = occupied == true && hasTemperatures && heat != null && vent != null &&
            evaluated.add(seat) && target != null && existing == null
        val action = if (turningOff) existing else if (turningOn) target else null
        if (action == null) return current()

        val dedicatedVent = if (turningOff) (heat ?: 0) == 0 else !action.heating && ventBounds != null
        val range = (if (dedicatedVent) ventBounds else heatBounds) ?: return current()
        val low = range.minValue ?: return current()
        val high = range.maxValue ?: return current()
        val requested = when {
            turningOff -> 0
            action.heating || dedicatedVent -> action.level.coerceAtMost(high)
            else -> (-action.level).coerceAtLeast(low)
        }
        if (requested !in low..high || (!turningOff && requested == 0) || !canWriteClimate(vehicle)) return current()
        if (turningOff) emptyAttempts[seat] = now
        val accepted = climateCommand { done, failed ->
            if (dedicatedVent) sdkSeat.setHvacSeatVentilation(area, requested, { _, _ -> done() }, failed)
            else sdkSeat.setHvacSeatTemperature(area, requested, { _, _ -> done() }, failed)
        }
        if (!accepted) return current()
        delay(250)
        if (dedicatedVent) vent = readVent() ?: vent else heat = readHeat() ?: heat
        if (turningOff && (if (dedicatedVent) vent else heat) == 0) emptyAttempts.remove(seat)
        return current()
    }

    private suspend fun readCabinTemperature(vehicle: Vehicle, area: SeatArea): Float? =
        readVehicleValue<Float> { done, failed ->
            vehicle.getHVAC().getHvacTemperature(area, TemperatureUnit.CELSIUS, done, failed)
        }?.takeIf { it.isFinite() }

    private fun seatsForValue(value: SeatValue): CabinSeat? = CabinSeat.entries.firstOrNull { it.value == value }

    private suspend fun updateCabinTemperature(vehicle: Vehicle, area: SeatArea, target: Float): Float? {
        if (!canWriteClimate(vehicle)) return null
        val accepted = climateCommand { done, failed ->
            vehicle.getHVAC().setHvacTemperature(area, target, TemperatureUnit.CELSIUS, { _, _ -> done() }, failed)
        }
        if (!accepted) return null
        delay(250)
        return readCabinTemperature(vehicle, area)
    }

    private suspend fun canWriteClimate(vehicle: Vehicle): Boolean =
        parking == ParkingStatus.Parked && sdk === vehicle &&
            withTimeoutOrNull(1_250) { readGear(vehicle) } == VehicleGear.GEAR_PARK &&
            parking == ParkingStatus.Parked && sdk === vehicle

    private suspend fun climateCommand(
        send: (onComplete: () -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    ): Boolean = withTimeoutOrNull(2_000) {
        suspendCancellableCoroutine { result ->
            val failure: (Exception) -> Unit = { error ->
                Log.w(TAG, "Climate command failed", error)
                if (result.isActive) result.resume(false)
            }
            try {
                send({ if (result.isActive) result.resume(true) }, failure)
            } catch (error: Exception) {
                failure(error)
            }
        }
    } ?: false

    suspend fun readPassengerOccupied(): Boolean? = sdk?.let { readSeatOccupied(it, CabinSeat.Passenger.value) }

    /** Turns front temperature sync (SYNC) on or off, parked only, and returns the state the car reports after. */
    suspend fun setFrontSync(on: Boolean): Boolean? {
        val vehicle = sdk ?: return null
        if (!canWriteClimate(vehicle)) return null
        val accepted = climateCommand { done, failed ->
            vehicle.getHVAC().setHvacDual(FrontSeats, on, { _, _ -> done() }, failed)
        }
        if (!accepted) return null
        delay(250)
        return readFrontSync(vehicle)
    }

    private suspend fun readSeatOccupied(vehicle: Vehicle, seat: SeatValue): Boolean? =
        readVehicleValue<Boolean> { done, failed ->
            vehicle.getSeat().getCurrentSeatOccupancy(SeatArea(seat), done, failed)
        }?.xor(invertedSeatReading)

    private suspend fun readFrontSync(vehicle: Vehicle): Boolean? = withTimeoutOrNull(1_250) {
        suspendCancellableCoroutine { result ->
            runCatching {
                vehicle.getHVAC().getHvacDualState(
                    FrontSeats,
                    { value -> if (result.isActive) result.resume(value) },
                    { error ->
                        Log.d(TAG, "Front climate sync unavailable: ${error.message}")
                        if (result.isActive) result.resume(null)
                    },
                )
            }.onFailure { error ->
                Log.d(TAG, "Front climate sync unavailable: ${error.message}")
                if (result.isActive) result.resume(null)
            }
        }
    }

    fun stop() {
        connection++
        readJob?.cancel()
        readJob = null
        gear = null
        properties = null
        clearRestrictions()
        car?.disconnect()
        car = null
        sdk?.release()
        sdk = null
    }

    private fun clearRestrictions() {
        val manager = restrictions
        restrictions = null
        requiresOptimization = null
        runCatching { manager?.unregisterListener() }
    }

    private companion object {
        const val TAG = "OrbitVehicle"
        const val CLIMATE_CONTROL = "pleos.car.permission.CONTROL_CAR_CLIMATE"
        const val SEAT_CONTROL = "pleos.car.permission.CONTROL_CAR_SEATS"
        const val DOOR_READ = "pleos.car.permission.CAR_DOORS"
        const val DOOR_CONTROL = "pleos.car.permission.CONTROL_CAR_DOORS"
        val FrontSeats = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT)
        val PassengerDoors = listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)
        val AllPassengerDoors = DoorArea(*PassengerDoors.toTypedArray())
    }
}
