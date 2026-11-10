package org.ward.adhkar.data

import kotlin.math.abs

/** Maps the vertical movement on the card's upper-edge handle into a navigation action. */
object WardSwipe {
    const val NONE = 0
    const val NEXT = 1
    const val PREVIOUS = -1

    /**
     * Upward = next dhikr, downward = previous dhikr.
     * A dead zone prevents incidental finger movement from changing the displayed dhikr.
     */
    fun action(distancePx: Float, thresholdPx: Float): Int {
        if (!distancePx.isFinite() || !thresholdPx.isFinite()) return NONE
        val threshold = abs(thresholdPx)
        if (threshold <= 0f) return NONE
        return when {
            distancePx <= -threshold -> NEXT
            distancePx >= threshold -> PREVIOUS
            else -> NONE
        }
    }
}
