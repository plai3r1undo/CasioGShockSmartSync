package org.avmedia.gshockGoogleSync.data.sleep

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.LocalDate

/**
 * Free-text notes per day (e.g. "late coffee", "ran 10 km"), keyed by the night's wake date,
 * so the sleep charge of that night can be read next to what happened. Stored in
 * `notes.json` as `{"2026-11-23": "text"}`.
 */
class NoteStore(private val dir: File) {
    private val gson = Gson()
    private val file get() = File(dir, FILE_NAME)

    fun load(): Map<LocalDate, String> = parse(runCatching { file.readText() }.getOrNull())

    /** Sets the note for [date]; a blank [text] removes it. Returns all notes. */
    fun set(date: LocalDate, text: String): Map<LocalDate, String> {
        val notes = load().toMutableMap()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) notes.remove(date) else notes[date] = trimmed
        save(notes)
        return notes
    }

    /** Adds the notes of [other] for days that have no note yet. Returns all notes. */
    fun mergeMissing(other: Map<LocalDate, String>): Map<LocalDate, String> {
        val notes = load()
        val merged = other.filterKeys { it !in notes } + notes
        if (merged.size != notes.size) save(merged)
        return merged
    }

    private fun save(notes: Map<LocalDate, String>) {
        dir.mkdirs()
        val json = gson.toJson(notes.toSortedMap().mapKeys { it.key.toString() })
        val tmp = File(dir, "$FILE_NAME.tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
    }

    companion object {
        const val FILE_NAME = "notes.json"

        /** Parses a notes.json; unreadable files and invalid dates are ignored. */
        fun parse(json: String?): Map<LocalDate, String> {
            if (json.isNullOrBlank()) return emptyMap()
            val raw: Map<String, String>? = runCatching {
                Gson().fromJson<Map<String, String>>(json, object : TypeToken<Map<String, String>>() {}.type)
            }.getOrNull()
            return raw.orEmpty().mapNotNull { (key, text) ->
                val date = runCatching { LocalDate.parse(key) }.getOrNull()
                if (date == null || text.isNullOrBlank()) null else date to text
            }.toMap()
        }
    }
}
