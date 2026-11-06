package org.ward.adhkar.data

import android.content.Context
import org.json.JSONArray

/** The corpus is bundled locally. No network access or remote data provider. */
enum class Period(val id: String, val label: String) {
    MORNING("morning", "الصباح"), EVENING("evening", "المساء");
    companion object {
        fun fromId(value: String?): Period = entries.firstOrNull { it.id == value } ?: MORNING
    }
}

data class Dhikr(
    val id: String,
    val title: String,
    val period: String,
    val target: Int,
    val morningText: String,
    val eveningText: String,
    val source: String,
    val note: String
) {
    fun belongsTo(p: Period): Boolean = period == "both" || period == p.id
    fun content(p: Period): String = if (p == Period.MORNING) morningText else eveningText
}

object DhikrRepository {
    fun load(context: Context): List<Dhikr> {
        val raw = context.assets.open("adhkar.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            Dhikr(
                id = value.getString("id"),
                title = value.getString("title"),
                period = value.getString("period"),
                target = value.getInt("target"),
                morningText = value.getString("morningText"),
                eveningText = value.getString("eveningText"),
                source = value.getString("source"),
                note = value.optString("note", "")
            )
        }
    }
}
