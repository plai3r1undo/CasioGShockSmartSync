package org.avmedia.gshockGoogleSync.sleep

import org.avmedia.gshockGoogleSync.data.sleep.StoredNight
import org.avmedia.gshockGoogleSync.ui.health.sleepChargeTrend
import org.avmedia.gshockGoogleSync.ui.health.trendWindowEnd
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SleepChargeTrendTest {
    private val zone = ZoneId.of("Europe/Rome")

    private fun night(start: String, spanMin: Int, charge: Int): StoredNight {
        val s = Instant.parse(start)
        return StoredNight(
            recordDate = start.take(10), startEpochMs = s.toEpochMilli(),
            endEpochMs = s.plusSeconds(spanMin * 60L).toEpochMilli(), sleepCharge = charge, ansCharge = 0f,
            spanMin = spanMin, actualSleepMin = spanMin, remMin = 0, lightMin = 0, deepMin = 0,
            heartRate = 0, heartRateBaseline = 0, hrvMs = 0, hrvBaselineMs = 0,
            breathingRate = 0f, breathingRateBaseline = 0f,
        )
    }

    @Test
    fun oneSlotPerWakeDateWithGaps() {
        val nights = listOf(
            night("2026-11-18T02:10:00Z", 354, 60), // wakes 18 Nov (local)
            night("2026-11-21T22:49:00Z", 507, 67), // wakes 22 Nov
            night("2026-11-22T23:36:00Z", 531, 75), // wakes 23 Nov
            night("2026-11-22T13:00:00Z", 60, 20), // nap, also "22 Nov": the longer night wins
        )
        val trend = sleepChargeTrend(nights, LocalDate.parse("2026-11-23"), 7, zone)
        assertEquals(LocalDate.parse("2026-11-17"), trend.first().date)
        assertEquals(listOf(null, 60, null, null, null, 67, 75), trend.map { it.charge })
    }

    @Test
    fun windowStaysPutWhileTappingInsideItAndCanAlwaysGoForward() {
        val newest = LocalDate.parse("2026-11-25")
        fun d(s: String) = LocalDate.parse(s)
        // Tapping an older bar inside the window keeps the window (was: moved to end there).
        val end = trendWindowEnd(null, d("2026-11-15"), newest, 14)
        assertEquals(newest, end)
        // Tapping a newer bar again still shows the same window.
        assertEquals(newest, trendWindowEnd(end, d("2026-11-20"), newest, 14))
        // Going before the window moves it, leaving half a window of newer days to tap.
        val moved = trendWindowEnd(end, d("2026-11-05"), newest, 14)
        assertEquals(d("2026-11-12"), moved)
        // ...and moving forward past its end brings it back, never past the newest night.
        assertEquals(d("2026-11-20"), trendWindowEnd(moved, d("2026-11-13"), newest, 14))
        assertEquals(newest, trendWindowEnd(moved, d("2026-11-24"), newest, 14))
    }
}
