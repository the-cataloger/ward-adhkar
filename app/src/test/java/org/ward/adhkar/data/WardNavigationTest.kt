package org.ward.adhkar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WardNavigationTest {
    private fun entry(id: String) = Dhikr(
        id = id, title = id, period = "both", target = 1,
        morningText = id, eveningText = id, source = "", note = ""
    )
    private val entries = listOf(entry("a"), entry("b"), entry("c"), entry("d"))

    @Test fun firstUnfinishedSkipsCompletedEntries() {
        assertEquals(2, WardNavigation.firstUnfinished(entries) { it.id in setOf("a", "b") })
    }

    @Test fun nextUnfinishedSkipsCompletedAndWraps() {
        assertEquals(3, WardNavigation.nextUnfinished(entries, 1) { it.id in setOf("a", "b", "c") })
        assertEquals(0, WardNavigation.nextUnfinished(entries, 3) { it.id in setOf("b", "c", "d") })
    }

    @Test fun fullSessionHasNoUnfinishedEntry() {
        assertNull(WardNavigation.firstUnfinished(entries) { true })
        assertNull(WardNavigation.nextUnfinished(entries, 2) { true })
    }

    @Test fun emptyAndSingleItemListsAreSafe() {
        assertNull(WardNavigation.firstUnfinished(emptyList()) { false })
        assertNull(WardNavigation.nextUnfinished(listOf(entry("x")), 0) { false })
        assertNull(WardNavigation.nextUnfinished(entries, -1) { false })
    }
