package com.example.orbitx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeagueMeasurementTest {
    @Test
    fun chargingBetweenDrivesDoesNotCountAsEnergyUse() {
        val start = LeagueVehicleSample(100f, 60f)
        val firstStop = LeagueVehicleSample(112f, 58f)
        val charged = LeagueVehicleSample(112f, 70f)
        val secondStop = LeagueVehicleSample(118f, 68f)

        val first = requireNotNull(evDrivingInterval(start, firstStop))
        assertNull(evDrivingInterval(firstStop, charged))
        val second = requireNotNull(evDrivingInterval(charged, secondStop))

        assertEquals(18f, first.distanceKm + second.distanceKm, 0f)
        assertEquals(4f, first.energyKwh + second.energyKwh, 0f)
    }

    @Test
    fun invalidAndStationaryIntervalsDoNotEnterTheEstimate() {
        val baseline = LeagueVehicleSample(100f, 60f)

        assertNull(evDrivingInterval(baseline, LeagueVehicleSample(99f, 59f)))
        assertNull(evDrivingInterval(baseline, LeagueVehicleSample(100f, 59f)))
        assertNull(evDrivingInterval(baseline, LeagueVehicleSample(110f, 65f)))
        assertNull(evDrivingInterval(baseline, LeagueVehicleSample(110f, null)))
    }
}
