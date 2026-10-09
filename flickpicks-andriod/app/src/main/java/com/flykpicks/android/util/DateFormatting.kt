package com.flykpicks.android.util

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/** Server dates are ISO-8601 strings, with or without fractional seconds. */
object DateFormatting {

    private const val PLACEHOLDER = "—"

    fun parseInstant(iso: String): Instant? =
        try {
            OffsetDateTime.parse(iso).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }

    /** e.g. "Oct 7, 2026"; "—" when missing or unparseable. */
    fun displayDate(
        iso: String?,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = format(iso, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM), zone, locale)

    /** e.g. "Oct 7, 2026, 9:12 PM"; "—" when missing or unparseable. */
    fun displayDateTime(
        iso: String?,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = format(
        iso,
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT),
        zone,
        locale,
    )

    private fun format(iso: String?, formatter: DateTimeFormatter, zone: ZoneId, locale: Locale): String {
        val instant = iso?.let(::parseInstant) ?: return PLACEHOLDER
        return formatter.withLocale(locale).withZone(zone).format(instant)
    }
}
