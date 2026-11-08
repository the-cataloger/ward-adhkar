package org.ward.adhkar.data

/** Keeps navigation independent of the UI and never sends the reader to a finished dhikr. */
object WardNavigation {
    fun firstUnfinished(entries: List<Dhikr>, isFinished: (Dhikr) -> Boolean): Int? =
        entries.indexOfFirst { !isFinished(it) }.takeIf { it >= 0 }

    /** Search forward, wrapping once to earlier items, but never revisit the current item. */
    fun nextUnfinished(
        entries: List<Dhikr>,
        currentIndex: Int,
        isFinished: (Dhikr) -> Boolean
    ): Int? {
        if (entries.size < 2 || currentIndex !in entries.indices) return null
        for (offset in 1 until entries.size) {
            val candidate = (currentIndex + offset) % entries.size
            if (!isFinished(entries[candidate])) return candidate
        }
        return null
    }
}
