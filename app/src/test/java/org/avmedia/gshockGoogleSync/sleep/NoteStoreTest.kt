package org.avmedia.gshockGoogleSync.sleep

import org.avmedia.gshockGoogleSync.data.sleep.HealthBackup
import org.avmedia.gshockGoogleSync.data.sleep.NoteStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

class NoteStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val d1 = LocalDate.of(2026, 11, 22)
    private val d2 = LocalDate.of(2026, 11, 23)

    @Test
    fun setTrimsPersistsAndBlankDeletes() {
        val dir = tmp.newFolder()
        NoteStore(dir).set(d1, "  late coffee \n")
        NoteStore(dir).set(d2, "ran 10 km")
        assertEquals(mapOf(d1 to "late coffee", d2 to "ran 10 km"), NoteStore(dir).load())
        assertEquals(mapOf(d2 to "ran 10 km"), NoteStore(dir).set(d1, "   "))
    }

    @Test
    fun parseIgnoresBadDatesAndGarbage() {
        assertEquals(mapOf(d1 to "ok"), NoteStore.parse("""{"2026-11-22":"ok","nope":"x","2026-11-23":""}"""))
        assertTrue(NoteStore.parse("not json").isEmpty())
    }

    @Test
    fun backupRoundTripKeepsPhoneNotes() {
        val source = tmp.newFolder("a").also { NoteStore(it).set(d1, "from backup"); NoteStore(it).set(d2, "backup d2") }
        val zip = ByteArrayOutputStream().also { HealthBackup(source).export(it) }.toByteArray()

        val target = tmp.newFolder("b").also { NoteStore(it).set(d2, "on phone") }
        val result = HealthBackup(target).import(ByteArrayInputStream(zip))
        assertEquals(mapOf(d1 to "from backup", d2 to "backup d2"), result.notes)
        assertEquals(mapOf(d1 to "from backup", d2 to "on phone"), NoteStore(target).mergeMissing(result.notes))
        assertTrue(File(target, "notes.json").isFile)
    }
}
