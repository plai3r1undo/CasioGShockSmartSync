package org.avmedia.gshockGoogleSync.data.sleep

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What an import brought in. */
data class HealthImportResult(
    /** Raw life-log files that were not on the phone yet. */
    val rawFilesAdded: Int,
    /** Nights from the backup's nights.json (merged; nights already on the phone are kept). */
    val nights: List<StoredNight>,
    /** Day notes from the backup's notes.json (notes already on the phone are kept). */
    val notes: Map<java.time.LocalDate, String> = emptyMap(),
)

/**
 * Zip backup of the health data in `files/sleep/`:
 * `sleep/nights.json`, `sleep/activity.json`, `sleep/notes.json` and every raw life-log file
 * under `sleep/raw/`.
 *
 * The raw files are the source of truth (the watch sends each record only once), so an import
 * adds the missing raw files and the caller re-decodes nights and activity from them. The
 * backup's nights.json is also returned, for nights whose raw files were never archived.
 */
class HealthBackup(private val dir: File) {
    private val rawDir get() = File(dir, "raw")

    /** Writes the backup to [out] (not closed). Returns the number of files written. */
    fun export(out: OutputStream): Int {
        var count = 0
        val zip = ZipOutputStream(out)
        fun add(name: String, file: File) {
            zip.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            count++
        }
        TOP_LEVEL_FILES.map { File(dir, it) }.filter { it.isFile }.forEach { add("$PREFIX${it.name}", it) }
        rawDir.listFiles().orEmpty()
            .filter { it.isFile && RAW_NAME.matches(it.name) }
            .sortedBy { it.name }
            .forEach { add("${PREFIX}raw/${it.name}", it) }
        zip.finish()
        return count
    }

    /**
     * Reads a backup from [input] (not closed). Only known file names are accepted, so a
     * crafted zip cannot write outside `raw/`; existing raw files are never overwritten.
     * Throws [IllegalArgumentException] if [input] contains no health data.
     */
    fun import(input: InputStream): HealthImportResult {
        var added = 0
        var recognised = 0
        var nights = emptyList<StoredNight>()
        var notes = emptyMap<java.time.LocalDate, String>()
        val zip = ZipInputStream(input)
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name
            when {
                entry.isDirectory -> Unit
                name == "${PREFIX}nights.json" -> {
                    recognised++
                    val bytes = zip.readBounded()
                    nights = runCatching {
                        Gson().fromJson<List<StoredNight>>(
                            String(bytes, Charsets.UTF_8),
                            object : TypeToken<List<StoredNight>>() {}.type,
                        ).orEmpty()
                    }.getOrDefault(emptyList())
                }
                name.startsWith("${PREFIX}raw/") && RAW_NAME.matches(name.removePrefix("${PREFIX}raw/")) -> {
                    recognised++
                    val bytes = zip.readBounded()
                    val target = File(rawDir, name.removePrefix("${PREFIX}raw/"))
                    if (!target.exists()) {
                        rawDir.mkdirs()
                        target.writeBytes(bytes)
                        added++
                    }
                }
                name == "${PREFIX}activity.json" -> recognised++ // rebuilt from the raw files
                name == "${PREFIX}${NoteStore.FILE_NAME}" -> {
                    recognised++
                    notes = NoteStore.parse(String(zip.readBounded(), Charsets.UTF_8))
                }
            }
            zip.closeEntry()
        }
        require(recognised > 0) { "Not a health data backup" }
        return HealthImportResult(added, nights, notes)
    }

    private fun ZipInputStream.readBounded(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            require(out.size() <= MAX_ENTRY_BYTES) { "Backup entry too large" }
        }
        return out.toByteArray()
    }

    private companion object {
        const val PREFIX = "sleep/"
        const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
        val TOP_LEVEL_FILES = listOf("nights.json", "activity.json", NoteStore.FILE_NAME)
        val RAW_NAME = Regex("""\d{8}-\d{6}_[0-9a-f]{2}_[0-9a-f]{2}\.bin""")
    }
}
