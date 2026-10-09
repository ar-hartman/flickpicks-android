package com.flykpicks.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class DateFormattingTest {

    @Test
    fun parsesIsoWithAndWithoutFractionalSecondsAndOffsets() {
        val expected = Instant.parse("2026-10-07T21:12:43Z")
        assertEquals(expected, DateFormatting.parseInstant("2026-10-07T21:12:43Z"))
        assertEquals(expected.plusMillis(123), DateFormatting.parseInstant("2026-10-07T21:12:43.123Z"))
        assertEquals(expected, DateFormatting.parseInstant("2026-10-07T14:12:43-07:00"))
    }

    @Test
    fun rejectsNonIsoInput() {
        assertNull(DateFormatting.parseInstant("2026-10-07"))
        assertNull(DateFormatting.parseInstant("yesterday"))
    }

    @Test
    fun displayDate_formatsInZone_orShowsPlaceholder() {
        assertEquals("Oct 7, 2026", DateFormatting.displayDate("2026-10-07T21:12:43Z", ZoneOffset.UTC, Locale.US))
        assertEquals("Oct 8, 2026", DateFormatting.displayDate("2026-10-07T21:12:43Z", ZoneOffset.ofHours(9), Locale.US))
        assertEquals("—", DateFormatting.displayDate(null))
        assertEquals("—", DateFormatting.displayDate("not a date"))
    }

    @Test
    fun displayDateTime_includesTime() {
        val text = DateFormatting.displayDateTime("2026-10-07T21:12:43Z", ZoneOffset.UTC, Locale.US)
        assertTrue(text, text.startsWith("Oct 7, 2026") && text.contains("9:12"))
    }
}
