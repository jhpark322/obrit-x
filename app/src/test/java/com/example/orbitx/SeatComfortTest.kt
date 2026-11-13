package com.example.orbitx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeatComfortTest {
    @Test
    fun nearSetpointLeavesSeatControlsAlone() {
        assertNull(seatComfortTarget(22f))
        assertNull(seatComfortTarget(24f))
        assertNull(seatComfortTarget(Float.NaN))
    }

    @Test
    fun temperatureDifferenceFrom23DeterminesDirectionAndLevel() {
        assertEquals(SeatComfort(true, 1), seatComfortTarget(19.1f))
        assertEquals(SeatComfort(true, 2), seatComfortTarget(17.5f))
        assertEquals(SeatComfort(false, 1), seatComfortTarget(25.5f))
        assertEquals(SeatComfort(false, 2), seatComfortTarget(28.5f))
    }
}
