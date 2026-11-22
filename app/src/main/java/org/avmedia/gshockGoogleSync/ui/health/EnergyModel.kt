package org.avmedia.gshockGoogleSync.ui.health

import org.avmedia.gshockGoogleSync.data.sleep.StoredActivity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private const val SLOT_MINUTES = 5L

/** One 5-minute slot of a day; [kcal] / [steps] are null where the watch sent no data. */
data class EnergySlot(val start: Instant, val kcal: Float?, val steps: Int?)

/**
 * Energy for one local day, from the watch's 5-minute bins (life-log 0x51).
 * The watch counts resting energy too, so the day total is never zero.
 */
data class EnergyDay(
    val date: LocalDate,
    val slots: List<EnergySlot>,
    /** The watch's own total for the day (last value received), if any. */
    val watchTotalKcal: Int?,
    /** Typical kcal per 5 minutes at rest, or null if unknown. */
    val restingKcalPer5: Float?,
) {
    private val known get() = slots.filter { it.kcal != null }

    val hasData: Boolean get() = known.isNotEmpty()
    val binTotalKcal: Float get() = known.sumOf { it.kcal!!.toDouble() }.toFloat()

    /** What to show as the day total: the watch's figure when present (it also covers missing slots). */
    val totalKcal: Int get() = watchTotalKcal ?: binTotalKcal.roundToInt()

    /** Energy above the resting level, over the slots we have. */
    val activeKcal: Int?
        get() = restingKcalPer5?.let { rest ->
            known.sumOf { (it.kcal!! - rest).coerceAtLeast(0f).toDouble() }.roundToInt()
        }

    val peak: EnergySlot? get() = known.maxByOrNull { it.kcal!! }
    val firstKnown: EnergySlot? get() = known.firstOrNull()
    val lastKnown: EnergySlot? get() = known.lastOrNull()
}

/** Days that have energy data, newest first. */
fun StoredActivity.energyDates(zone: ZoneId = ZoneId.systemDefault()): List<LocalDate> =
    bins.filter { it.kcal != null }
        .map { Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate() }
        .distinct()
        .sortedDescending()

/**
 * Resting energy per 5 minutes: the most common value among slots without steps.
 * The watch reports a constant value while still (990/128 = 7.7 kcal for this user).
 */
fun StoredActivity.restingKcalPer5(): Float? =
    bins.filter { it.kcal != null && it.steps == 0 }
        .groupingBy { it.kcal!! }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key

fun StoredActivity.energyDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): EnergyDay {
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val count = (Duration.between(start, end).toMinutes() / SLOT_MINUTES).toInt() // 276/288/300 with DST
    val byStart = bins.associateBy { it.startEpochMs }
    val slots = List(count) { i ->
        val slotStart = start.plus(Duration.ofMinutes(SLOT_MINUTES * i))
        val bin = byStart[slotStart.toEpochMilli()]
        EnergySlot(slotStart, bin?.kcal, bin?.steps)
    }
    return EnergyDay(
        date = date,
        slots = slots,
        watchTotalKcal = day(date)?.kcal,
        restingKcalPer5 = restingKcalPer5(),
    )
}
