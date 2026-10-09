package com.hellohealth.ui.charts

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Turns an ISO `localDate` (yyyy-MM-dd) into a friendly relative "last recorded" label vs [today]:
 * "today", "yesterday", "N days ago", "N weeks ago", "N months ago", "N years ago". Pure + null-safe:
 * a null/blank/unparseable date or a future date returns null (caller shows nothing).
 */
fun relativeLastRecorded(isoDate: String?, today: LocalDate = LocalDate.now()): String? {
    if (isoDate.isNullOrBlank()) return null
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return null
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days < 0L -> null // future — nothing sensible to say
        days == 0L -> "today"
        days == 1L -> "yesterday"
        days < 7L -> "$days days ago"
        days < 14L -> "1 week ago"
        days < 30L -> "${days / 7} weeks ago"
        days < 60L -> "1 month ago"
        days < 365L -> "${days / 30} months ago"
        days < 730L -> "1 year ago"
        else -> "${days / 365} years ago"
    }
}
