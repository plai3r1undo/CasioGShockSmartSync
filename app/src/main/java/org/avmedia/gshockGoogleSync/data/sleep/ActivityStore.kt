package org.avmedia.gshockGoogleSync.data.sleep

import androidx.annotation.Keep
import com.google.gson.Gson
import org.avmedia.gshockapi.model.LifeLogActivity
import timber.log.Timber
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Steps and energy (kcal, incl. resting) in the 5-minute interval starting at [startEpochMs]. */
@Keep
data class StoredStepBin(val startEpochMs: Long, val steps: Int, val kcal: Float? = null)

/** The watch's latest daily totals for [date] (local date), as of [updatedEpochMs]. */
@Keep
data class StoredDay(
    val date: String,
    val steps: Int,
    val kcal: Int,
    val activeMin: Int?,
    val updatedEpochMs: Long,
)

@Keep
data class StoredActivity(
    val bins: List<StoredStepBin> = emptyList(),
    val days: List<StoredDay> = emptyList(),
    /**
     * Decoder version that produced this file; older files are rebuilt from the raw archive.
     * Defaults to 0 so files written before versioning (which Gson reads through the no-arg
     * constructor) count as old; [ActivityStore.merge] stamps [CURRENT_VERSION].
     */
    val version: Int = 0,
) {
    companion object {
        /** 2: per-interval energy (0x51). */
        const val CURRENT_VERSION = 2
    }

    fun day(date: LocalDate): StoredDay? = days.firstOrNull { it.date == date.toString() }

    fun stepsBetween(from: Instant, to: Instant): Int =
        bins.filter { it.startEpochMs >= from.toEpochMilli() && it.startEpochMs < to.toEpochMilli() }
            .sumOf { it.steps }

    /**
     * Steps on [date]. The watch's own total is authoritative while it is fresh; the bins
     * cover the rest of the day when the last snapshot was taken before midnight.
     */
    fun stepsOn(date: LocalDate, zone: ZoneId): Int {
        val start = date.atStartOfDay(zone).toInstant()
        val fromBins = stepsBetween(start, date.plusDays(1).atStartOfDay(zone).toInstant())
        return maxOf(day(date)?.steps ?: 0, fromBins)
    }

    /** Steps per hour for the [count] hours ending with the current one (oldest first). */
    fun hourlySteps(now: Instant, zone: ZoneId, count: Int): List<Int> {
        val currentHour = now.atZone(zone).truncatedTo(ChronoUnit.HOURS)
        return (count - 1 downTo 0).map { back ->
            val start = currentHour.minusHours(back.toLong())
            stepsBetween(start.toInstant(), start.plusHours(1).toInstant())
        }
    }
}

/**
 * Daytime activity received from the watch, stored in `sleep/activity.json`. Like nights,
 * the watch sends every 5-minute bin only once, so it is persisted right after each transfer.
 */
class ActivityStore(private val dir: File) {
    private val gson = Gson()
    private val file get() = File(dir, "activity.json")

    @Synchronized
    fun load(): StoredActivity =
        runCatching {
            if (!file.exists()) return StoredActivity()
            gson.fromJson(file.readText(), StoredActivity::class.java) ?: StoredActivity()
        }.onFailure { Timber.e(it, "Cannot read stored activity") }
            .getOrDefault(StoredActivity())
            // Gson leaves missing fields null despite the Kotlin defaults.
            .let { StoredActivity(it.bins.orEmpty(), it.days.orEmpty(), it.version) }

    /** Deletes the stored activity (before a rebuild from the raw archive). */
    @Synchronized
    fun clear() {
        file.delete()
    }

    @Synchronized
    fun merge(activities: List<LifeLogActivity>, zone: ZoneId = ZoneId.systemDefault()): StoredActivity {
        if (activities.isEmpty()) return load()
        val current = load()
        val bins = current.bins.associateBy { it.startEpochMs }.toMutableMap()
        val days = current.days.associateBy { it.date }.toMutableMap()
        activities.sortedBy { it.timestamp }.forEach { a ->
            a.intervals.forEach {
                bins[it.start.toEpochMilli()] = StoredStepBin(it.start.toEpochMilli(), it.steps, it.kcal)
            }
            val date = a.timestamp.atZone(zone).toLocalDate().toString()
            val previous = days[date]
            if (previous == null || previous.updatedEpochMs <= a.timestamp.toEpochMilli()) {
                days[date] = StoredDay(
                    date = date,
                    steps = a.todaySteps,
                    kcal = a.todayKcal,
                    activeMin = a.activeTime?.toMinutes()?.toInt(),
                    updatedEpochMs = a.timestamp.toEpochMilli(),
                )
            }
        }
        val keepBinsFrom = activities.maxOf { it.timestamp }.minus(BIN_RETENTION).toEpochMilli()
        val merged = StoredActivity(
            bins = bins.values.filter { it.startEpochMs >= keepBinsFrom }.sortedBy { it.startEpochMs },
            days = days.values.sortedBy { it.date },
            version = StoredActivity.CURRENT_VERSION,
        )
        dir.mkdirs()
        val tmp = File(dir, "activity.json.tmp")
        tmp.writeText(gson.toJson(merged))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
        return merged
    }

    private companion object {
        val BIN_RETENTION: Duration = Duration.ofDays(60)
    }
}
