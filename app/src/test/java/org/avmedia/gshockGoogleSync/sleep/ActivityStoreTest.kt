package org.avmedia.gshockGoogleSync.sleep

import org.avmedia.gshockGoogleSync.data.sleep.ActivityStore
import org.avmedia.gshockGoogleSync.data.sleep.StoredActivity
import org.avmedia.gshockGoogleSync.ui.health.toStepCounterData
import org.avmedia.gshockapi.model.LifeLogActivity
import org.avmedia.gshockapi.model.StepInterval
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ActivityStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Europe/Rome")

    private fun activity(at: String, steps: Int, vararg bins: Pair<String, Int>) = LifeLogActivity(
        timestamp = Instant.parse(at),
        todaySteps = steps,
        todayKcal = steps / 2,
        activeTime = Duration.ofMinutes(steps / 100L),
        intervals = bins.map { (start, n) -> StepInterval(Instant.parse(start), n, kcal = 7.7f) },
    )

    @Test
    fun mergesTransfersAndBuildsCardData() {
        val store = ActivityStore(tmp.root)
        // Evening of the 21st (local), then a transfer after local midnight.
        store.merge(listOf(activity("2026-11-21T21:00:00Z", 5000, "2026-11-21T20:50:00Z" to 100)), zone)
        store.merge(listOf(activity("2026-11-22T15:22:00Z", 1801,
            "2026-11-21T22:30:00Z" to 300, // 23:30 local on the 21st, after the last snapshot
            "2026-11-22T14:10:00Z" to 40,
            "2026-11-22T15:15:00Z" to 21)), zone)

        val stored = ActivityStore(tmp.root).load()
        assertEquals(5000, stored.day(LocalDate.parse("2026-11-21"))!!.steps)
        // Bins after the snapshot are not lost: snapshot 5000 vs bins 400 -> 5000 (bins are a subset).
        assertEquals(5000, stored.stepsOn(LocalDate.parse("2026-11-21"), zone))
        assertEquals(18, stored.day(LocalDate.parse("2026-11-22"))!!.activeMin)

        val card = stored.toStepCounterData(Instant.parse("2026-11-22T15:30:00Z"), zone)
        assertEquals(1801, card.currentDaySteps)
        assertEquals(listOf(0, 0, 0, 0, 0, 5000), card.dailyHistory)
        assertEquals(10, card.hourlySteps.size)
        assertEquals(listOf(40, 21), card.hourlySteps.takeLast(2)) // 15:00 and 16:00 local
    }

    @Test
    fun keepsEnergyAndFlagsOldFiles() {
        val store = ActivityStore(tmp.root)
        store.merge(listOf(activity("2026-11-22T15:22:00Z", 10, "2026-11-22T15:15:00Z" to 10)), zone)
        val loaded = store.load()
        assertEquals(7.7f, loaded.bins.single().kcal!!, 0f)
        assertEquals(StoredActivity.CURRENT_VERSION, loaded.version)
        // A file from the first version (no version, no kcal) must be detected as old.
        java.io.File(tmp.root, "activity.json").writeText("""{"bins":[{"startEpochMs":1,"steps":2}],"days":[]}""")
        assertEquals(0, store.load().version)
    }
}
