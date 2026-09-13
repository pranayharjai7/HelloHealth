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

/**
 * v4 → v5: add the P0.5 onboarding vitals to `profile`.
 *
 * Purely additive `ALTER TABLE ... ADD COLUMN` — SQLite appends each column to the end of the
 * table, matching the field order in [com.hellohealth.data.local.entities.ProfileEntity] (vitals
 * declared after the Syncable overrides). Existing displayName-only rows keep all data and read
 * back with null/default vitals. Enum vitals are stored as their `name` string; the NOT NULL
 * columns (`unitPreference`, `hasOnboarded`) carry defaults so existing rows are valid.
 *
 * Column types match Room's generated v5 affinities (validated against 5.json by MigrationTest).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `gender` TEXT")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `birthDateEpochDay` INTEGER")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `heightCm` REAL")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `weightKg` REAL")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `activityLevel` TEXT")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `goalType` TEXT")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `targetWeightKg` REAL")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `targetRateKgPerWeek` REAL")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `unitPreference` TEXT NOT NULL DEFAULT 'METRIC'")
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `hasOnboarded` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v5 → v6: add the P1 `emotion_records` table (manual mood logging + future camera detection).
 *
 * A new multi-row, syncable table — CREATE only (like MIGRATION_3_4), no change to existing tables.
 * Column order and affinities must match Room's generated v6 schema exactly (validated against
 * 6.json by MigrationTest): feature columns first, the four Syncable sync-meta columns last, to
 * match [com.hellohealth.data.local.entities.EmotionRecordEntity]. `Double` → REAL, nullable
 * `Long?`/`String?` → no NOT NULL.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `emotion_records` (" +
                "`id` TEXT NOT NULL, " +
                "`userId` TEXT NOT NULL, " +
                "`timestampUtcEpochMs` INTEGER NOT NULL, " +
                "`tzOffsetMinutes` INTEGER NOT NULL, " +
                "`localDate` TEXT NOT NULL, " +
                "`emotion` TEXT NOT NULL, " +
                "`confidence` REAL NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`note` TEXT, " +
                "`visibility` TEXT NOT NULL, " +
                "`updatedAtEpochMs` INTEGER NOT NULL, " +
                "`updatedAtTzOffsetMinutes` INTEGER NOT NULL, " +
                "`deletedAtEpochMs` INTEGER, " +
                "`isSynced` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}

/**
 * v6 → v7: add the P1 `isDynamicTheme` flag to `profile`.
 *
 * Additive `ALTER TABLE ADD COLUMN` (like MIGRATION_4_5's onboarding columns). SQLite appends the
 * column, matching [com.hellohealth.data.local.entities.ProfileEntity] where `isDynamicTheme` is
 * declared last. NOT NULL with DEFAULT 1 (Boolean true) backfills existing rows to the feature's
 * default-on behavior. Validated against 7.json by MigrationTest.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `isDynamicTheme` INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v7 → v8: add the Phase-A `workout_sessions` table (native manual workout logging).
 *
 * A new multi-row, syncable table — CREATE only (like MIGRATION_5_6), no change to existing tables,
 * so the read-only Health Connect activity path is untouched. Column order and affinities must match
 * Room's generated v8 schema exactly (validated against 8.json by MigrationTest): feature columns
 * first, the four Syncable sync-meta columns last, to match
 * [com.hellohealth.data.local.entities.WorkoutSessionEntity]. `Long` → INTEGER, `Double?` → REAL
 * (nullable → no NOT NULL), nullable `String?` → no NOT NULL.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workout_sessions` (" +
                "`id` TEXT NOT NULL, " +
                "`userId` TEXT NOT NULL, " +
                "`activityType` TEXT NOT NULL, " +
                "`title` TEXT, " +
                "`startTimeUtcEpochMs` INTEGER NOT NULL, " +
                "`endTimeUtcEpochMs` INTEGER NOT NULL, " +
                "`durationMinutes` INTEGER NOT NULL, " +
                "`calories` REAL, " +
                "`distanceKm` REAL, " +
                "`note` TEXT, " +
                "`localDate` TEXT NOT NULL, " +
                "`updatedAtEpochMs` INTEGER NOT NULL, " +
                "`updatedAtTzOffsetMinutes` INTEGER NOT NULL, " +
                "`deletedAtEpochMs` INTEGER, " +
                "`isSynced` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
    }
}
