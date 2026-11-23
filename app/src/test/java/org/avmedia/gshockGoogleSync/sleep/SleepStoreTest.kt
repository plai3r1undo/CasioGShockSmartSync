package org.avmedia.gshockGoogleSync.sleep

import org.avmedia.gshockGoogleSync.data.sleep.SleepStore
import org.avmedia.gshockGoogleSync.data.sleep.StoredNight
import org.avmedia.gshockGoogleSync.ui.health.formatAnsCharge
import org.avmedia.gshockGoogleSync.ui.health.formatHm
import org.avmedia.gshockapi.model.LifeLogFile
import org.avmedia.gshockapi.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SleepStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun night(date: String, charge: Int) = StoredNight(
        recordDate = date, startEpochMs = 1_000, endEpochMs = 2_000, sleepCharge = charge, ansCharge = 0.386f,
        spanMin = 507, actualSleepMin = 454, remMin = 108, lightMin = 242, deepMin = 104,
        heartRate = 55, heartRateBaseline = 57, hrvMs = 70, hrvBaselineMs = 74,
        breathingRate = 13.93f, breathingRateBaseline = 13.85f,
        stageOffsetsSec = listOf(0, 30, 840), stageCodes = listOf(-2, -5, -2),
    )

    @Test
    fun mergePersistsAndReplacesByDate() {
        val store = SleepStore(tmp.root)
        store.merge(listOf(night("2026-11-19", 88), night("2026-11-21", 60)))
        store.merge(listOf(night("2026-11-21", 67)))

        val reloaded = SleepStore(tmp.root).load()
        assertEquals(listOf("2026-11-21", "2026-11-19"), reloaded.map { it.recordDate })
        assertEquals(67, reloaded.first().sleepCharge)
        assertEquals(53, reloaded.first().interruptionsMin)
        assertEquals(listOf(Triple(0L, 30L, SleepStage.WAKE), Triple(30L, 840L, SleepStage.LIGHT)), reloaded.first().segments())
    }

    @Test
    fun archivesRawFiles() {
        val store = SleepStore(tmp.root)
        store.archiveRaw(listOf(LifeLogFile(0x2E, 0x5A, byteArrayOf(1, 2, 3))))
        val files = tmp.root.resolve("raw").listFiles()!!.map { it.name }
        assertTrue(files.single().endsWith("_2e_5a.bin"))
    }

    @Test
    fun formatsLikeOfficialApp() {
        assertEquals("+0.3", formatAnsCharge(0.386f))
        assertEquals("-10.0", formatAnsCharge(-10f))
        assertEquals("-7.9", formatAnsCharge(-7.998f))
        assertEquals("8:27", formatHm(507))
    }

    @Test
    fun labelsNightByWakeDateAndTracksVersion() {
        // Watch date 2026-11-22, slept 00:36-09:27 CET on the 23rd.
        val n = night("2026-11-22", 75).copy(
            startEpochMs = java.time.Instant.parse("2026-11-22T23:36:00Z").toEpochMilli(),
            endEpochMs = java.time.Instant.parse("2026-11-23T08:27:00Z").toEpochMilli(),
        )
        assertEquals(java.time.LocalDate.parse("2026-11-23"), n.wakeDate(java.time.ZoneId.of("Europe/Rome")))

        val store = SleepStore(tmp.root)
        assertEquals(1, store.nightsVersion())
        store.setNightsVersion(2)
        assertEquals(2, SleepStore(tmp.root).nightsVersion())
    }
}
