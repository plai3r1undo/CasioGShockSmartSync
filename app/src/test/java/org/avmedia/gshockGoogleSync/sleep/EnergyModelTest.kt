package org.avmedia.gshockGoogleSync.sleep

import org.avmedia.gshockGoogleSync.data.sleep.StoredActivity
import org.avmedia.gshockGoogleSync.data.sleep.StoredDay
import org.avmedia.gshockGoogleSync.data.sleep.StoredStepBin
import org.avmedia.gshockGoogleSync.ui.health.energyDates
import org.avmedia.gshockGoogleSync.ui.health.energyDay
import org.avmedia.gshockGoogleSync.ui.health.restingKcalPer5
import org.avmedia.gshockGoogleSync.ui.health.visibleSpanLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EnergyModelTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val rest = 990 / 128f

    private fun bin(utc: String, steps: Int, units: Int) =
        StoredStepBin(Instant.parse(utc).toEpochMilli(), steps, units / 128f)

    private val activity = StoredActivity(
        bins = listOf(
            bin("2026-11-21T22:55:00Z", 0, 990),  // 23:55 local on the 21st
            bin("2026-11-22T10:25:00Z", 0, 990),
            bin("2026-11-22T10:30:00Z", 0, 990),
            bin("2026-11-22T10:35:00Z", 297, 1961),
            bin("2026-11-22T14:00:00Z", 32, 2395),
        ),
        days = listOf(StoredDay("2026-11-22", 1801, 1614, 50, 0)),
    )

    @Test
    fun restingLevelIsTheCommonStillValue() {
        assertEquals(rest, activity.restingKcalPer5()!!, 0f)
    }

    @Test
    fun buildsLocalDayWithSlotsActiveAndPeak() {
        assertEquals(listOf(LocalDate.parse("2026-11-22"), LocalDate.parse("2026-11-21")), activity.energyDates(zone))

        val day = activity.energyDay(LocalDate.parse("2026-11-22"), zone)
        assertEquals(288, day.slots.size)
        assertEquals(4, day.slots.count { it.kcal != null })
        // 11:25 local = slot 137
        assertEquals(rest, day.slots[137].kcal!!, 0f)
        assertNull(day.slots[136].kcal)
        assertEquals(1614, day.totalKcal) // the watch's total wins over the partial bins
        assertEquals(((1961 - 990) + (2395 - 990)) / 128f, day.activeKcal!!.toFloat(), 1f)
        assertEquals(2395 / 128f, day.peak!!.kcal!!, 0f)
        assertEquals(32, day.peak!!.steps)
    }

    @Test
    fun dstDaysHaveMoreOrFewerSlots() {
        assertEquals(300, activity.energyDay(LocalDate.parse("2026-10-25"), zone).slots.size)
        assertEquals(276, activity.energyDay(LocalDate.parse("2026-03-29"), zone).slots.size)
    }

    @Test
    fun dayWithoutWatchTotalUsesBins() {
        val day = activity.energyDay(LocalDate.parse("2026-11-21"), zone)
        assertEquals(8, day.totalKcal) // 7.7 rounded
    }

    @Test
    fun zoomLabelShowsVisibleSpan() {
        assertEquals("24 h", visibleSpanLabel(1f))
        assertEquals("6 h", visibleSpanLabel(4f))
        assertEquals("2 h", visibleSpanLabel(12f))
    }

    @Test
    fun dayWithoutAnyDataHasNoData() {
        val day = activity.energyDay(LocalDate.parse("2026-11-10"), zone)
        assertEquals(false, day.hasData)
        assertNull(day.peak)
    }
}
