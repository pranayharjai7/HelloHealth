package com.hellohealth.data.local

/**
 * Compile-time contract for every Room entity that participates in offline-first sync.
 *
 * Room cannot inherit columns from an abstract `@Entity`, so this is a plain Kotlin
 * interface: each entity declares the four sync columns itself and implements this so
 * the sync engine can treat them uniformly.
 *
 *  - [updatedAtEpochMs]        UTC epoch millis of the last local write — the LWW clock.
 *  - [updatedAtTzOffsetMinutes] Local UTC offset at write time. Audit/display only; never
 *                              used to decide Last-Write-Wins.
 *  - [deletedAtEpochMs]        Tombstone: null = live row, non-null = soft-deleted at that time.
 *  - [isSynced]               false on every local write; set true after a successful push.
 */
interface Syncable {
    val updatedAtEpochMs: Long
    val updatedAtTzOffsetMinutes: Int
    val deletedAtEpochMs: Long?
    val isSynced: Boolean
}
