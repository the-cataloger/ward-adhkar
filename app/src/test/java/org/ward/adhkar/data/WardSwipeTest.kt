package org.ward.adhkar.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WardSwipeTest {
    @Test fun upwardGestureGoesToNext() {
        assertEquals(WardSwipe.NEXT, WardSwipe.action(-48f, 42f))
        assertEquals(WardSwipe.NEXT, WardSwipe.action(-42f, 42f))
    }

    @Test fun downwardGestureGoesToPrevious() {
        assertEquals(WardSwipe.PREVIOUS, WardSwipe.action(90f, 42f))
        assertEquals(WardSwipe.PREVIOUS, WardSwipe.action(42f, 42f))
    }

    @Test fun minorMovementDoesNothing() {
        assertEquals(WardSwipe.NONE, WardSwipe.action(0f, 42f))
        assertEquals(WardSwipe.NONE, WardSwipe.action(41f, 42f))
        assertEquals(WardSwipe.NONE, WardSwipe.action(-41f, 42f))
    }

    @Test fun invalidDistanceOrThresholdIsIgnored() {
        assertEquals(WardSwipe.NONE, WardSwipe.action(Float.NaN, 42f))
        assertEquals(WardSwipe.NONE, WardSwipe.action(48f, 0f))
        assertEquals(WardSwipe.NONE, WardSwipe.action(48f, Float.POSITIVE_INFINITY))
    }
}
