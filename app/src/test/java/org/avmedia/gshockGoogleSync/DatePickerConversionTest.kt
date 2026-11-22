package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.ui.common.dateToUtcMillis
import org.avmedia.gshockGoogleSync.ui.common.utcMillisToDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class DatePickerConversionTest {
    @Test
    fun roundTripsInEveryTimeZone() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("Europe/Rome", "America/Los_Angeles", "Pacific/Kiritimati", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (date in listOf("2026-11-22", "2026-03-29", "2026-10-25", "2024-02-29")) {
                    val d = LocalDate.parse(date)
                    assertEquals("$zone $date", d, utcMillisToDate(dateToUtcMillis(d)))
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
        assertEquals(1795305600000L, dateToUtcMillis(LocalDate.parse("2026-11-22")))
    }
}
