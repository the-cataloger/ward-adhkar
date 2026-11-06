package org.ward.adhkar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderClockTest {
    private val zone = ZoneId.of("Africa/Cairo")

    @Test fun schedulesTodayIfFuture() {
        val now = ZonedDateTime.of(2026, 11, 6, 5, 0, 0, 0, zone)
        val next = ReminderClock.nextOccurrence(now, LocalTime.of(6, 15))
        assertEquals(6, next.hour)
        assertEquals(6, next.dayOfMonth)
    }

    @Test fun schedulesTomorrowIfPassed() {
        val now = ZonedDateTime.of(2026, 11, 6, 9, 0, 0, 0, zone)
        val next = ReminderClock.nextOccurrence(now, LocalTime.of(6, 0))
        assertEquals(7, next.dayOfMonth)
        assertTrue(next.isAfter(now))
    }

    @Test fun schedulesTomorrowWhenExactlyDue() {
        val now = ZonedDateTime.of(2026, 11, 6, 17, 0, 0, 0, zone)
        val next = ReminderClock.nextOccurrence(now, LocalTime.of(17, 0))
        assertEquals(7, next.dayOfMonth)
    }
}
