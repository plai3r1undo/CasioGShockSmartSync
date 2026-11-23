package org.avmedia.gshockGoogleSync.data.sleep

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.avmedia.gshockapi.model.LifeLogFile
import org.avmedia.gshockapi.model.NightlyRecharge
import org.avmedia.gshockapi.model.SleepStage
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One stored night. Plain fields only, so Gson can (de)serialize it without java.time adapters.
 * Durations are minutes as reported by the watch.
 */
@Keep
data class StoredNight(
    val recordDate: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val sleepCharge: Int,
    val ansCharge: Float,
    val spanMin: Int,
    val actualSleepMin: Int,
    val remMin: Int,
    val lightMin: Int,
    val deepMin: Int,
    val heartRate: Int,
    val heartRateBaseline: Int,
    val hrvMs: Int,
    val hrvBaselineMs: Int,
    val breathingRate: Float,
    val breathingRateBaseline: Float,
    /** Hypnogram: seconds since sleep start of each stage change, and the stage (Polar code). */
    val stageOffsetsSec: List<Long>? = null,
    val stageCodes: List<Int>? = null,
    val rawHex: String = "",
) {
    /** The watch's date for the night: the UTC date of the sleep start (unique key). */
    val date: LocalDate get() = LocalDate.parse(recordDate)

    /** Date shown to the user: the local date on which the night ended (the morning). */
    fun wakeDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = end.atZone(zone).toLocalDate()
    val start: Instant get() = Instant.ofEpochMilli(startEpochMs)
    val end: Instant get() = Instant.ofEpochMilli(endEpochMs)
    val interruptionsMin: Int get() = (spanMin - actualSleepMin).coerceAtLeast(0)

    /** Stage segments as (startSec, endSec, stage); empty when no hypnogram was received. */
    fun segments(): List<Triple<Long, Long, SleepStage>> {
        val offsets = stageOffsetsSec ?: return emptyList()
        val codes = stageCodes ?: return emptyList()
        return offsets.indices.toList().zipWithNext().mapNotNull { (a, b) ->
            SleepStage.fromCode(codes[a])?.let { Triple(offsets[a], offsets[b], it) }
        }
    }

    companion object {
        fun from(n: NightlyRecharge): StoredNight =
            StoredNight(
                recordDate = n.recordDate.toString(),
                startEpochMs = n.sleepStart.toEpochMilli(),
                endEpochMs = n.sleepEnd.toEpochMilli(),
                sleepCharge = n.sleepCharge,
                ansCharge = n.ansCharge,
                spanMin = n.sleepSpan.toMinutes().toInt(),
                actualSleepMin = n.actualSleep.toMinutes().toInt(),
                remMin = n.rem.toMinutes().toInt(),
                lightMin = n.light.toMinutes().toInt(),
                deepMin = n.deep.toMinutes().toInt(),
                heartRate = n.heartRateBpm,
                heartRateBaseline = n.heartRateBaselineBpm,
                hrvMs = n.hrvMs,
                hrvBaselineMs = n.hrvBaselineMs,
                breathingRate = n.breathingRate,
                breathingRateBaseline = n.breathingRateBaseline,
                stageOffsetsSec = n.hypnogram?.transitions?.map { it.offset.seconds },
                stageCodes = n.hypnogram?.transitions?.map { it.stage.code },
                rawHex = n.raw.joinToString("") { "%02x".format(it) },
            )
    }
}

/**
 * File-backed store for sleep nights. The watch sends every night only once, so everything
 * received is written to disk immediately: decoded nights to `sleep/nights.json`, and every
 * raw life-log file to `sleep/raw/` so data that is not decoded yet can be recovered later.
 */
class SleepStore(private val dir: File) {
    private val gson = Gson()
    private val nightsFile get() = File(dir, "nights.json")
    private val versionFile get() = File(dir, "nights.version")
    private val rawDir get() = File(dir, "raw")

    @Synchronized
    fun load(): List<StoredNight> =
        runCatching {
            if (!nightsFile.exists()) return emptyList()
            val type = object : TypeToken<List<StoredNight>>() {}.type
            gson.fromJson<List<StoredNight>>(nightsFile.readText(), type).orEmpty()
        }.onFailure { Timber.e(it, "Cannot read stored sleep nights") }
            .getOrDefault(emptyList())
            .sortedByDescending { it.recordDate }

    /** Adds or replaces nights (keyed by record date). Returns the merged list, newest first. */
    @Synchronized
    fun merge(newNights: List<StoredNight>): List<StoredNight> {
        val merged = (load().associateBy { it.recordDate } + newNights.associateBy { it.recordDate })
            .values.sortedByDescending { it.recordDate }
        dir.mkdirs()
        val tmp = File(dir, "nights.json.tmp")
        tmp.writeText(gson.toJson(merged))
        if (!tmp.renameTo(nightsFile)) {
            nightsFile.writeText(tmp.readText())
            tmp.delete()
        }
        return merged
    }

    /**
     * Decoder version of the stored nights. 2: sleep start = record date at the UTC bedtime
     * (version 1 put nights starting after local midnight a day early).
     */
    @Synchronized
    fun nightsVersion(): Int = runCatching { versionFile.readText().trim().toInt() }.getOrDefault(1)

    @Synchronized
    fun setNightsVersion(version: Int) {
        dir.mkdirs()
        versionFile.writeText(version.toString())
    }

    @Synchronized
    fun archiveRaw(files: List<LifeLogFile>, receivedAt: Instant = Instant.now()) {
        if (files.isEmpty()) return
        rawDir.mkdirs()
        val stamp = RAW_STAMP.format(receivedAt.atZone(ZoneId.systemDefault()))
        files.forEach { f ->
            File(rawDir, "%s_%02x_%02x.bin".format(stamp, f.category, f.id)).writeBytes(f.data)
        }
    }

    /** Every archived transfer (files grouped by receive time), oldest first. */
    @Synchronized
    fun archivedTransfers(): List<List<LifeLogFile>> =
        rawDir.listFiles().orEmpty()
            .mapNotNull { f -> RAW_NAME.matchEntire(f.name)?.let { f to it.destructured } }
            .groupBy { (_, parts) -> parts.component1() }
            .toSortedMap().values
            .map { group ->
                group.map { (f, parts) ->
                    LifeLogFile(parts.component2().toInt(16), parts.component3().toInt(16), f.readBytes())
                }
            }

    private companion object {
        val RAW_NAME = Regex("""(\d{8}-\d{6})_([0-9a-f]{2})_([0-9a-f]{2})\.bin""")
        val RAW_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
