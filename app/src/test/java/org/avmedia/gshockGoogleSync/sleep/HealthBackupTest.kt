package org.avmedia.gshockGoogleSync.sleep

import org.avmedia.gshockGoogleSync.data.sleep.HealthBackup
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HealthBackupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val nightsJson = """[{"recordDate":"2026-11-22","startEpochMs":1,"endEpochMs":2,"sleepCharge":75,""" +
        """"ansCharge":3.6,"spanMin":531,"actualSleepMin":486,"remMin":110,"lightMin":300,"deepMin":75,""" +
        """"heartRate":53,"heartRateBaseline":57,"hrvMs":72,"hrvBaselineMs":72,"breathingRate":13.7,""" +
        """"breathingRateBaseline":13.9}]"""

    @Test
    fun roundTripAddsOnlyMissingRawFiles() {
        val source = tmp.newFolder("a").also { dir ->
            File(dir, "nights.json").writeText(nightsJson)
            File(dir, "raw").mkdirs()
            File(dir, "raw/20261123-103852_2e_52.bin").writeBytes(byteArrayOf(0x52, 9, 0, 0, 0, 1, 2, 4, 8))
            File(dir, "raw/20261123-103852_2e_00.bin").writeBytes(byteArrayOf(0, 1))
            File(dir, "raw/notes.txt").writeText("ignored")
        }
        val zip = ByteArrayOutputStream().also { assertEquals(3, HealthBackup(source).export(it)) }.toByteArray()

        val target = tmp.newFolder("b").also { dir ->
            File(dir, "raw").mkdirs()
            File(dir, "raw/20261123-103852_2e_00.bin").writeBytes(byteArrayOf(9, 9)) // already there: kept
        }
        val result = HealthBackup(target).import(ByteArrayInputStream(zip))

        assertEquals(1, result.rawFilesAdded)
        assertEquals(listOf(75), result.nights.map { it.sleepCharge })
        assertArrayEquals(byteArrayOf(9, 9), File(target, "raw/20261123-103852_2e_00.bin").readBytes())
        assertArrayEquals(
            File(source, "raw/20261123-103852_2e_52.bin").readBytes(),
            File(target, "raw/20261123-103852_2e_52.bin").readBytes(),
        )
    }

    @Test
    fun ignoresUnknownAndEscapingEntries() {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                listOf("sleep/raw/../../evil.bin", "../evil.bin", "sleep/raw/x.bin", "sleep/raw/20261123-103852_2e_52.bin")
                    .forEach { z.putNextEntry(ZipEntry(it)); z.write(1); z.closeEntry() }
            }
        }.toByteArray()
        val dir = tmp.newFolder("c")
        assertEquals(1, HealthBackup(dir).import(ByteArrayInputStream(zip)).rawFilesAdded)
        assertFalse(File(tmp.root, "evil.bin").exists())
        assertEquals(listOf("20261123-103852_2e_52.bin"), File(dir, "raw").list()!!.toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherZips() {
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("photo.jpg")); z.write(1); z.closeEntry() }
        }.toByteArray()
        HealthBackup(tmp.newFolder("d")).import(ByteArrayInputStream(zip))
    }
}
