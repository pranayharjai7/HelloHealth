package com.hellohealth.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unambiguous identifier for "one user's one local day".
 *
 * Health/activity data is bucketed per calendar day in the user's local timezone, but
 * rows are stored/synced with UTC-epoch timestamps (see [Timestamps]). Passing a bare
 * [LocalDate] around loses the "whose day, in which zone" context; [DayKey] carries it
 * explicitly so repositories and syncers agree on the bucket a record belongs to.
 */
data class DayKey(
    val userId: String,
    val localDate: LocalDate
) {
    /** Stable string form, e.g. for map keys or log lines. */
    fun asString(): String = "$userId|$localDate"

    companion object {
        /** Resolve the local day an [instant] falls on for [userId] in [zone]. */
        fun from(
            userId: String,
            instant: Instant,
            zone: ZoneId = ZoneId.systemDefault()
        ): DayKey = DayKey(userId, instant.atZone(zone).toLocalDate())
    }
}
