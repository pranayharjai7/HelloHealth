package com.hellohealth.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 → v4: introduce the offline-first sync schema.
 *
 * v3 shipped only the (unused) `users` table. This migration is purely additive — it CREATEs
 * the new sync tables and leaves `users` untouched, so no existing data is lost. The dead
 * `users` table is left in place; a later cleanup phase can drop it.
 *
 * The CREATE TABLE statements must match Room's generated v4 schema exactly (column order,
 * affinities, NOT NULL, defaults, PK). The exported schema JSON (app/schemas) is the source of
 * truth; MIGRATION_3_4 is validated against it by MigrationTest.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (" +
                "`userId` TEXT NOT NULL, " +
                "`steps` INTEGER NOT NULL, " +
                "`activeCalories` INTEGER NOT NULL, " +
                "`activeMinutes` INTEGER NOT NULL, " +
                "`updatedAtEpochMs` INTEGER NOT NULL, " +
                "`updatedAtTzOffsetMinutes` INTEGER NOT NULL, " +
                "`deletedAtEpochMs` INTEGER, " +
                "`isSynced` INTEGER NOT NULL, " +
                "PRIMARY KEY(`userId`))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profile` (" +
                "`userId` TEXT NOT NULL, " +
                "`displayName` TEXT, " +
                "`updatedAtEpochMs` INTEGER NOT NULL, " +
                "`updatedAtTzOffsetMinutes` INTEGER NOT NULL, " +
                "`deletedAtEpochMs` INTEGER, " +
                "`isSynced` INTEGER NOT NULL, " +
                "PRIMARY KEY(`userId`))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_prefs` (" +
                "`userId` TEXT NOT NULL, " +
                "`dietType` TEXT NOT NULL, " +
                "`allergies` TEXT NOT NULL, " +
                "`cuisinePreferences` TEXT NOT NULL, " +
                "`updatedAtEpochMs` INTEGER NOT NULL, " +
                "`updatedAtTzOffsetMinutes` INTEGER NOT NULL, " +
                "`deletedAtEpochMs` INTEGER, " +
                "`isSynced` INTEGER NOT NULL, " +
                "PRIMARY KEY(`userId`))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `snapshot` (" +
                "`userId` TEXT NOT NULL, " +
                "`snapshotDate` TEXT NOT NULL, " +
                "`snapshotTimezone` TEXT NOT NULL, " +
                "`syncStatus` TEXT NOT NULL, " +
                "`dataSource` TEXT NOT NULL, " +
                "`lastSyncedAtEpochMs` INTEGER NOT NULL, " +
                "`summaryJson` TEXT NOT NULL, " +
                "`updatedAtEpochMs` INTEGER NOT NULL, " +
                "`updatedAtTzOffsetMinutes` INTEGER NOT NULL, " +
                "`deletedAtEpochMs` INTEGER, " +
                "`isSynced` INTEGER NOT NULL, " +
                "PRIMARY KEY(`userId`, `snapshotDate`))"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_log` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`runAtEpochMs` INTEGER NOT NULL, " +
                "`featureTag` TEXT NOT NULL, " +
                "`pushed` INTEGER NOT NULL, " +
                "`pulled` INTEGER NOT NULL, " +
                "`conflicts` INTEGER NOT NULL, " +
                "`failures` INTEGER NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, " +
                "`resultLabel` TEXT NOT NULL)"
        )
    }
}
