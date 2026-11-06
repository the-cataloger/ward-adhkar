package org.ward.adhkar.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

/** On-device persistence. Counts belong to a local date, period and dhikr ID. */
class WardStore(context: Context) {
    private val prefs = context.getSharedPreferences("ward_settings", Context.MODE_PRIVATE)

    fun count(period: Period, id: String, date: LocalDate = LocalDate.now()): Int =
        prefs.getInt("count_${date}_${period.id}_$id", 0)

    fun setCount(period: Period, id: String, value: Int, date: LocalDate = LocalDate.now()) {
        prefs.edit().putInt("count_${date}_${period.id}_$id", value.coerceAtLeast(0)).apply()
    }

    fun completedCount(period: Period, dhikr: List<Dhikr>, date: LocalDate = LocalDate.now()): Int =
        dhikr.count { it.belongsTo(period) && count(period, it.id, date) >= it.target }

    fun periodTotal(period: Period, dhikr: List<Dhikr>): Int = dhikr.count { it.belongsTo(period) }

    fun isSessionComplete(period: Period, dhikr: List<Dhikr>, date: LocalDate = LocalDate.now()): Boolean {
        val total = periodTotal(period, dhikr)
        return total > 0 && completedCount(period, dhikr, date) == total
    }

    fun isFavorite(id: String): Boolean = prefs.getBoolean("favorite_$id", false)
    fun toggleFavorite(id: String): Boolean {
        val next = !isFavorite(id)
        prefs.edit().putBoolean("favorite_$id", next).apply()
        return next
    }

    var fontScale: Float
        get() = prefs.getFloat("font_scale", 1f).coerceIn(.80f, 1.40f)
        set(value) { prefs.edit().putFloat("font_scale", value.coerceIn(.80f, 1.40f)).apply() }

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) { prefs.edit().putBoolean("haptics", value).apply() }

    var countSoundEnabled: Boolean
        get() = prefs.getBoolean("count_sound", false)
        set(value) { prefs.edit().putBoolean("count_sound", value).apply() }

    var speechRate: Float
        get() = prefs.getFloat("speech_rate", .92f).coerceIn(.65f, 1.20f)
        set(value) { prefs.edit().putFloat("speech_rate", value.coerceIn(.65f, 1.20f)).apply() }

    var autoAdvance: Boolean
        get() = prefs.getBoolean("auto_advance", false)
        set(value) { prefs.edit().putBoolean("auto_advance", value).apply() }

    fun isReminderEnabled(period: Period): Boolean = prefs.getBoolean("enabled_${period.id}", false)
    fun setReminderEnabled(period: Period, value: Boolean) {
        prefs.edit().putBoolean("enabled_${period.id}", value).apply()
    }

    fun reminderTime(period: Period): LocalTime =
        LocalTime.of(
            prefs.getInt("hour_${period.id}", if (period == Period.MORNING) 6 else 17),
            prefs.getInt("minute_${period.id}", 0)
        )

    fun setReminderTime(period: Period, time: LocalTime) {
        prefs.edit().putInt("hour_${period.id}", time.hour)
            .putInt("minute_${period.id}", time.minute).apply()
    }
}
