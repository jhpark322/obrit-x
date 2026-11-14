package com.example.orbitx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrivalBatteryTest {
    private fun trip(predicted: Float?, actual: Float?, charged: Boolean = false) =
        TripReport("기본 전환", 12f, 2f, charged, pending = false, predictedArrivalKwh = predicted, actualArrivalKwh = actual)

    @Test
    fun usedMoreThanPredicted() = assertEquals(1.9f, trip(54.0f, 52.1f).arrivalGapKwh!!, 1e-4f)

    @Test
    fun usedLessThanPredicted() = assertEquals(-0.5f, trip(54.0f, 54.5f).arrivalGapKwh!!, 1e-4f)

    @Test
    fun noPredictionMeansNoGap() = assertNull(trip(null, 52.1f).arrivalGapKwh)

    @Test
    fun chargingOnTheWayIsNotCompared() = assertNull(trip(54.0f, 70f, charged = true).arrivalGapKwh)
}

class DriveBehaviorTest {
    @Test
    fun roundTripsThroughPrefsText() {
        val behavior = DriveBehavior(hardAccel = 4, hardBrake = 2, climateMinutes = 32, avgSpeedKph = 52)
        assertEquals(behavior, DriveBehavior.decode(behavior.encode()))
    }

    @Test
    fun missingAverageSpeedStaysMissing() =
        assertNull(DriveBehavior.decode(DriveBehavior(0, 0, 5, null).encode())!!.avgSpeedKph)

    @Test
    fun brokenTextIsIgnored() = assertNull(DriveBehavior.decode("1,2"))
}
