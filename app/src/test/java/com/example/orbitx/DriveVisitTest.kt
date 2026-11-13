package com.example.orbitx

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveVisitTest {
    @Test
    fun repeatedDestinationCountsDistinctDaysInTheLastSevenDays() {
        val today = LocalDate.of(2026, 11, 12)
        val visits = listOf(
            DriveVisit(today, "a"),
            DriveVisit(today, "a"),
            DriveVisit(today.minusDays(2), "a"),
            DriveVisit(today.minusDays(6), "a"),
            DriveVisit(today.minusDays(7), "a"),
            DriveVisit(today.plusDays(1), "a"),
            DriveVisit(today.minusDays(1), "b"),
        )

        assertEquals(3, repeatedDriveDays(visits, today))
        assertEquals(0, repeatedDriveDays(visits, today.plusDays(8)))
    }
}
