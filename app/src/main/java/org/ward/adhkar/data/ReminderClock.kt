package org.ward.adhkar.data

import java.time.LocalTime
import java.time.ZonedDateTime

/** Computes the next strictly future local reminder, including DST transitions. */
object ReminderClock {
    fun nextOccurrence(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val candidate = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (candidate.isAfter(now)) candidate else now.toLocalDate()
            .plusDays(1).atTime(time).atZone(now.zone)
    }
}
