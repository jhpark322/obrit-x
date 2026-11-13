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

    @Test
    fun separatePropertyChangesArePairedInEitherOrderAndOnlyOnce() {
        for (firstChange in listOf(LeagueVehicleSample(112f, 60f), LeagueVehicleSample(100f, 58f))) {
            val tracker = EvIntervalTracker()
            val end = LeagueVehicleSample(112f, 58f)
            assertNull(tracker.record(LeagueVehicleSample(100f, 60f), 0))
            assertNull(tracker.record(firstChange, 3_000))
            assertNull(tracker.record(firstChange, 12_000))
            assertEquals(EvEstimateTotals(12f, 2f), tracker.record(end, 20_000))
            assertNull(tracker.record(end, 23_000))
        }
    }

    @Test
    fun chargeSwitchAndBatteryIncreasesBreakPendingPairs() {
        for (switchOn in listOf(true, null)) {
            val tracker = EvIntervalTracker()
            tracker.record(LeagueVehicleSample(100f, 60f), 0)
            val before = tracker.record(LeagueVehicleSample(112f, 58f), 3_000)!!
            assertNull(tracker.record(LeagueVehicleSample(118f, 58f), 6_000))
            // An active switch blocks an otherwise plausible consumption interval, too.
            val chargeLevel = if (switchOn == true) 57f else 70f
            assertNull(tracker.record(LeagueVehicleSample(118f, chargeLevel, chargeSwitchOn = switchOn), 9_000))
            assertNull(tracker.record(LeagueVehicleSample(118f, 70f, chargeSwitchOn = false), 12_000))
            val after = tracker.record(LeagueVehicleSample(124f, 68f), 15_000)!!
            assertEquals(18f, before.distanceKm + after.distanceKm, 0f)
            assertEquals(4f, before.energyKwh + after.energyKwh, 0f)
        }
    }

    @Test
    fun expiredOrInterruptedPairsDoNotCountAsDriving() {
        for (interruption in listOf("expired", "gap", "missing")) {
            val tracker = EvIntervalTracker()
            val distanceOnly = LeagueVehicleSample(112f, 60f)
            tracker.record(LeagueVehicleSample(100f, 60f), 0)
            assertNull(tracker.record(distanceOnly, 3_000))
            val endAt = when (interruption) {
                "expired" -> {
                    for (at in listOf(12_000L, 22_000L, 32_000L)) tracker.record(distanceOnly, at)
                    35_000L
                }
                "missing" -> {
                    tracker.record(null, 6_000)
                    9_000L
                }
                else -> 21_000L
            }
            assertNull(tracker.record(LeagueVehicleSample(112f, 58f), endAt))
        }
    }
}
