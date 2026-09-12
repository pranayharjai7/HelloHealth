package com.hellohealth.core.time

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Timestamp conventions for offline-first sync.
 *
 * All sync clocks are **UTC epoch milliseconds** ([Long]) so Last-Write-Wins comparisons
 * are timezone-independent. Alongside each write we also record the local UTC offset in
 * minutes ([currentTzOffsetMinutes]) for audit/display only — it is never used to decide
 * LWW winners.
 */
object Timestamps {

    /** Current instant as UTC epoch millis — the canonical LWW clock. */
    fun nowEpochMs(): Long = Instant.now().toEpochMilli()

    /** The device's current UTC offset in minutes (e.g. IST = +330). Audit/display only. */
    fun currentTzOffsetMinutes(zone: ZoneId = ZoneId.systemDefault()): Int =
        zone.rules.getOffset(Instant.now()).totalSeconds / 60

    fun instantToEpochMs(instant: Instant): Long = instant.toEpochMilli()

    fun epochMsToInstant(epochMs: Long): Instant = Instant.ofEpochMilli(epochMs)

    /**
     * Parse a server timestamp (Supabase `timestamptz`, ISO-8601 with offset) to UTC epoch
     * millis. Mirrors the parse previously inlined in `ActivityRepositoryImpl`.
     *
     * Returns `null` when [value] is null or unparseable so callers can treat a missing/bad
     * server timestamp as "remote never wins" (epoch 0), keeping sync push-only and lossless
     * until the server `updated_at` column exists.
     */
    fun parseServerTimestamp(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
    }

    /** Format UTC epoch millis as an ISO-8601 UTC timestamp for sending to the server. */
    fun epochMsToServerTimestamp(epochMs: Long): String =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC).toString()
}
