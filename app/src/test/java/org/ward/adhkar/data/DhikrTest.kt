package org.ward.adhkar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DhikrTest {
    private fun make(period: String): Dhikr =
        Dhikr("demo", "ذكر تجريبي", period, 3, "نص الصباح", "نص المساء", "المصدر", "")

    @Test fun bothAppearsInBothSessions() {
        assertTrue(make("both").belongsTo(Period.MORNING))
        assertTrue(make("both").belongsTo(Period.EVENING))
    }

    @Test fun timeSpecificEntriesDoNotLeak() {
        assertTrue(make("morning").belongsTo(Period.MORNING))
        assertFalse(make("morning").belongsTo(Period.EVENING))
        assertTrue(make("evening").belongsTo(Period.EVENING))
        assertFalse(make("evening").belongsTo(Period.MORNING))
    }

    @Test fun usesPeriodSpecificText() {
        assertEquals("نص الصباح", make("both").content(Period.MORNING))
        assertEquals("نص المساء", make("both").content(Period.EVENING))
    }

    @Test fun invalidPeriodDefaultsToMorning() {
        assertEquals(Period.MORNING, Period.fromId("unexpected"))
    }
}
