package com.hellohealth.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Validates MIGRATION_3_4 against the exported v4 schema (app/schemas/.../4.json).
 *
 * v3 shipped only the `users` table with exportSchema=false, so 3.json was hand-authored to match
 * the v3 `users` DDL. runMigrationsAndValidate() creates the v3 DB, applies MIGRATION_3_4, then
 * structurally diffs the result against 4.json — any drift between the migration's CREATE TABLEs
 * and Room's generated schema fails the test.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `migrate 3 to 4 preserves users and creates sync tables`() {
        // Create v3 (users only) and seed a row so we can prove data survives.
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO users " +
                    "(email, password, name, avatarUrl, isGoogleUser, isLoggedIn, createdAt) " +
                    "VALUES ('a@b.com', 'pw', 'Ann', NULL, 0, 1, 123)"
            )
            close()
        }

        // Apply MIGRATION_3_4 and validate the resulting schema matches 4.json exactly.
        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        // Existing user data is untouched.
        db.query("SELECT name, isLoggedIn FROM users WHERE email = 'a@b.com'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Ann", c.getString(0))
            assertEquals(1, c.getInt(1))
        }

        // New sync tables exist and are queryable.
        for (table in listOf("goals", "profile", "food_prefs", "snapshot", "sync_log")) {
            db.query("SELECT count(*) FROM $table").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("$table should be empty after migration", 0, c.getInt(0))
            }
        }
        db.close()
    }

    @Test
    fun `migrate 4 to 5 adds profile vitals without losing existing rows`() {
        // Create v4 and seed a displayName-only profile row (pre-onboarding shape).
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                "INSERT INTO profile " +
                    "(userId, displayName, updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, isSynced) " +
                    "VALUES ('u1', 'Ann', 100, 0, NULL, 0)"
            )
            close()
        }

        // Apply MIGRATION_4_5 and validate the resulting schema matches 5.json exactly.
        val db = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)

        // Existing row survives; new columns read back as null / documented defaults.
        db.query(
            "SELECT displayName, gender, heightCm, unitPreference, hasOnboarded FROM profile WHERE userId = 'u1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Ann", c.getString(0))
            assertTrue("gender should be null", c.isNull(1))
            assertTrue("heightCm should be null", c.isNull(2))
            assertEquals("METRIC", c.getString(3))
            assertEquals(0, c.getInt(4))
        }
        db.close()
    }

    @Test
    fun `migrate 5 to 6 creates emotion_records and preserves existing rows`() {
        // Create v5 and seed a profile row so we can prove existing tables survive the new-table add.
        helper.createDatabase(dbName, 5).apply {
            execSQL(
                "INSERT INTO profile " +
                    "(userId, displayName, updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, " +
                    "isSynced, unitPreference, hasOnboarded) " +
                    "VALUES ('u1', 'Ann', 100, 0, NULL, 0, 'METRIC', 0)"
            )
            close()
        }

        // Apply MIGRATION_5_6 and validate the resulting schema matches 6.json exactly.
        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        // The pre-existing profile row is untouched by the additive new table.
        db.query("SELECT displayName FROM profile WHERE userId = 'u1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Ann", c.getString(0))
        }

        // The new emotion_records table exists, is empty, and accepts an insert (columns/affinities OK).
        db.query("SELECT count(*) FROM emotion_records").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.execSQL(
            "INSERT INTO emotion_records " +
                "(id, userId, timestampUtcEpochMs, tzOffsetMinutes, localDate, emotion, confidence, " +
                "source, note, visibility, updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, isSynced) " +
                "VALUES ('u1|100|HAPPINESS', 'u1', 100, 0, '2026-09-13', 'HAPPINESS', 1.0, " +
                "'manual', NULL, 'private', 100, 0, NULL, 0)"
        )
        db.query("SELECT emotion, confidence FROM emotion_records WHERE id = 'u1|100|HAPPINESS'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("HAPPINESS", c.getString(0))
            assertEquals(1.0, c.getDouble(1), 0.0001)
        }
        db.close()
    }

    @Test
    fun `migrate 6 to 7 adds isDynamicTheme defaulting existing rows to on`() {
        // Create v6 and seed a profile row lacking the new column.
        helper.createDatabase(dbName, 6).apply {
            execSQL(
                "INSERT INTO profile " +
                    "(userId, displayName, updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, " +
                    "isSynced, unitPreference, hasOnboarded) " +
                    "VALUES ('u1', 'Ann', 100, 0, NULL, 0, 'METRIC', 1)"
            )
            close()
        }

        // Apply MIGRATION_6_7 and validate the resulting schema matches 7.json exactly.
        val db = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        // Existing row survives and the new NOT NULL column backfills to 1 (default-on).
        db.query("SELECT displayName, isDynamicTheme FROM profile WHERE userId = 'u1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Ann", c.getString(0))
            assertEquals("existing rows default to dynamic theme on", 1, c.getInt(1))
        }
        db.close()
    }

    @Test
    fun `migrate 7 to 8 creates workout_sessions and preserves existing rows`() {
        // Create v7 and seed a profile row so we can prove existing tables survive the new-table add.
        helper.createDatabase(dbName, 7).apply {
            execSQL(
                "INSERT INTO profile " +
                    "(userId, displayName, updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, " +
                    "isSynced, unitPreference, hasOnboarded, isDynamicTheme) " +
                    "VALUES ('u1', 'Ann', 100, 0, NULL, 0, 'METRIC', 1, 1)"
            )
            close()
        }

        // Apply MIGRATION_7_8 WITHOUT schema validation. The flat `workout_sessions` vertical has been
        // removed from the app (WorkoutSessionEntity is deleted), so `AppDatabase` at v8 no longer
        // declares that table and a validating run would fail on the "extra" table. We still exercise
        // the real v7→v8 migration a genuine legacy user takes before v9 drops the table, and assert it
        // creates a usable `workout_sessions` and leaves existing rows intact. Structural validation of
        // the current schema is covered by the `migrate 8 to 9` case (added in Step 2/3).
        val db = helper.runMigrationsAndValidate(dbName, 8, false, MIGRATION_7_8)

        // The pre-existing profile row is untouched by the additive new table.
        db.query("SELECT displayName FROM profile WHERE userId = 'u1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Ann", c.getString(0))
        }

        // The new workout_sessions table exists, is empty, and accepts an insert (columns/affinities OK).
        db.query("SELECT count(*) FROM workout_sessions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        // Exercise every affinity, including the nullable REAL calories/distance and null title/note.
        db.execSQL(
            "INSERT INTO workout_sessions " +
                "(id, userId, activityType, title, startTimeUtcEpochMs, endTimeUtcEpochMs, durationMinutes, " +
                "calories, distanceKm, note, localDate, updatedAtEpochMs, updatedAtTzOffsetMinutes, " +
                "deletedAtEpochMs, isSynced) " +
                "VALUES ('w1', 'u1', 'RUN', 'Morning run', 1000, 2800000, 45, " +
                "320.5, 8.2, NULL, '2026-09-13', 2800000, 0, NULL, 0)"
        )
        db.query(
            "SELECT activityType, durationMinutes, calories, distanceKm FROM workout_sessions WHERE id = 'w1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("RUN", c.getString(0))
            assertEquals(45L, c.getLong(1))
            assertEquals(320.5, c.getDouble(2), 0.0001)
            assertEquals(8.2, c.getDouble(3), 0.0001)
        }
        db.close()
    }

    @Test
    fun `migrate 8 to 9 drops flat workout_sessions and creates the planning hierarchy`() {
        // Create v8 as a genuine legacy user would have it: a profile row plus a flat
        // workout_sessions row (the vertical this migration removes). We validate the migrated schema
        // against 9.json — this is the structural guard that MIGRATION_8_9's hand-written CREATE
        // TABLEs / indices exactly match Room's generated v9 entities.
        //
        // NOTE: createDatabase(v8) builds the schema from the CURRENT exported 8.json, which no longer
        // declares workout_sessions (WorkoutSessionEntity was deleted in Step 0 and 8.json regenerated).
        // A real legacy v8 user reached v8 via MIGRATION_7_8, which DID create the table — so to model
        // that user faithfully we recreate the flat table by hand (byte-identical to MIGRATION_7_8's
        // CREATE) before seeding it. This proves MIGRATION_8_9 drops it even when present.
        helper.createDatabase(dbName, 8).apply {
            execSQL(
                "INSERT INTO profile " +
                    "(userId, displayName, updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, " +
                    "isSynced, unitPreference, hasOnboarded, isDynamicTheme) " +
                    "VALUES ('u1', 'Ann', 100, 0, NULL, 0, 'METRIC', 1, 1)"
            )
            execSQL(
                "CREATE TABLE IF NOT EXISTS `workout_sessions` (" +
                    "`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `activityType` TEXT NOT NULL, " +
                    "`title` TEXT, `startTimeUtcEpochMs` INTEGER NOT NULL, " +
                    "`endTimeUtcEpochMs` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, " +
                    "`calories` REAL, `distanceKm` REAL, `note` TEXT, `localDate` TEXT NOT NULL, " +
                    "`updatedAtEpochMs` INTEGER NOT NULL, `updatedAtTzOffsetMinutes` INTEGER NOT NULL, " +
                    "`deletedAtEpochMs` INTEGER, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            execSQL(
                "INSERT INTO workout_sessions " +
                    "(id, userId, activityType, title, startTimeUtcEpochMs, endTimeUtcEpochMs, durationMinutes, " +
                    "calories, distanceKm, note, localDate, updatedAtEpochMs, updatedAtTzOffsetMinutes, " +
                    "deletedAtEpochMs, isSynced) " +
                    "VALUES ('w1', 'u1', 'RUN', 'Morning run', 1000, 2800000, 45, " +
                    "320.5, 8.2, NULL, '2026-09-13', 2800000, 0, NULL, 0)"
            )
            close()
        }

        // Apply MIGRATION_8_9 and validate the resulting schema matches 9.json exactly.
        val db = helper.runMigrationsAndValidate(dbName, 9, true, MIGRATION_8_9)

        // The pre-existing profile row is untouched by the drop + additive creates.
        db.query("SELECT displayName FROM profile WHERE userId = 'u1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Ann", c.getString(0))
        }

        // The flat vertical is gone: querying the dropped table throws.
        var flatTableGone = false
        try {
            db.query("SELECT count(*) FROM workout_sessions").use { it.moveToFirst() }
        } catch (e: Exception) {
            flatTableGone = true
        }
        assertTrue("workout_sessions must be dropped by MIGRATION_8_9", flatTableGone)

        // All four new tables exist and are empty.
        for (table in listOf("workout_plans", "workout_days", "planned_exercises", "exercises")) {
            db.query("SELECT count(*) FROM $table").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("$table should be empty after migration", 0, c.getInt(0))
            }
        }

        // workout_plans round-trips (Boolean/INTEGER isActive, planType, timestamps).
        db.execSQL(
            "INSERT INTO workout_plans " +
                "(id, userId, name, isActive, planType, createdAtEpochMs, " +
                "updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, isSynced) " +
                "VALUES ('p1', 'u1', 'Push/Pull/Legs', 1, 'WEEKLY', 500, 500, 0, NULL, 0)"
        )
        db.query("SELECT name, isActive, planType FROM workout_plans WHERE id = 'p1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Push/Pull/Legs", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals("WEEKLY", c.getString(2))
        }

        // workout_days round-trips.
        db.execSQL(
            "INSERT INTO workout_days " +
                "(id, planId, userId, slotKey, name, " +
                "updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, isSynced) " +
                "VALUES ('d1', 'p1', 'u1', 'MONDAY', 'Push', 500, 0, NULL, 0)"
        )
        db.query("SELECT slotKey, name FROM workout_days WHERE id = 'd1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("MONDAY", c.getString(0))
            assertEquals("Push", c.getString(1))
        }

        // planned_exercises: exercise every affinity. Row A has all optional targets set; row B leaves
        // every nullable target NULL (only targetSets required) — proving the nullable INTEGER/REAL
        // affinities round-trip both ways.
        db.execSQL(
            "INSERT INTO planned_exercises " +
                "(id, dayId, userId, exerciseId, orderIndex, targetSets, targetReps, targetWeightKg, " +
                "targetDurationSeconds, targetDistanceKm, targetSpeedKmh, targetIncline, " +
                "updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, isSynced) " +
                "VALUES ('pe1', 'd1', 'u1', 'ex-bench', 0, 4, 8, 60.5, " +
                "NULL, NULL, NULL, NULL, 500, 0, NULL, 0)"
        )
        db.execSQL(
            "INSERT INTO planned_exercises " +
                "(id, dayId, userId, exerciseId, orderIndex, targetSets, targetReps, targetWeightKg, " +
                "targetDurationSeconds, targetDistanceKm, targetSpeedKmh, targetIncline, " +
                "updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, isSynced) " +
                "VALUES ('pe2', 'd1', 'u1', 'ex-plank', 1, 3, NULL, NULL, " +
                "NULL, NULL, NULL, NULL, 500, 0, NULL, 0)"
        )
        db.query(
            "SELECT targetSets, targetReps, targetWeightKg FROM planned_exercises WHERE id = 'pe1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4, c.getInt(0))
            assertEquals(8, c.getInt(1))
            assertEquals(60.5, c.getDouble(2), 0.0001)
        }
        db.query(
            "SELECT targetSets, targetReps, targetWeightKg, targetDurationSeconds, targetDistanceKm, " +
                "targetSpeedKmh, targetIncline FROM planned_exercises WHERE id = 'pe2'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(3, c.getInt(0))
            assertTrue("targetReps should be null", c.isNull(1))
            assertTrue("targetWeightKg should be null", c.isNull(2))
            assertTrue("targetDurationSeconds should be null", c.isNull(3))
            assertTrue("targetDistanceKm should be null", c.isNull(4))
            assertTrue("targetSpeedKmh should be null", c.isNull(5))
            assertTrue("targetIncline should be null", c.isNull(6))
        }

        // exercises (read-only catalog, no sync columns) round-trips.
        db.execSQL(
            "INSERT INTO exercises " +
                "(id, name, category, primaryMuscles, secondaryMuscles, equipment, instructions, " +
                "gifUrl, youtubeQuery) " +
                "VALUES ('ex-bench', 'Bench Press', 'strength', '[\"chest\"]', '[\"triceps\"]', " +
                "'barbell', '[\"Lie on the bench\"]', 'https://example/bench.jpg', 'Bench Press tutorial')"
        )
        db.query("SELECT name, category, equipment FROM exercises WHERE id = 'ex-bench'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Bench Press", c.getString(0))
            assertEquals("strength", c.getString(1))
            assertEquals("barbell", c.getString(2))
        }
        db.close()
    }

    @Test
    fun `migrate 9 to 10 adds vitals_samples and preserves existing data`() {
        // A genuine v9 user has a profile row. MIGRATION_9_10 is purely additive: it CREATEs
        // vitals_samples and touches nothing else, so the profile must survive untouched.
        helper.createDatabase(dbName, 9).apply {
            execSQL(
                "INSERT INTO profile " +
                    "(userId, displayName, updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, " +
                    "isSynced, unitPreference, hasOnboarded, isDynamicTheme) " +
                    "VALUES ('u1', 'Ann', 100, 0, NULL, 0, 'METRIC', 1, 1)"
            )
            close()
        }

        // Apply MIGRATION_9_10 and validate the resulting schema matches 10.json exactly. This is
        // the structural guard that the hand-written CREATE TABLE / indices match Room's v10 entity.
        val db = helper.runMigrationsAndValidate(dbName, 10, true, MIGRATION_9_10)

        // The pre-existing profile row is untouched by the additive create.
        db.query("SELECT displayName FROM profile WHERE userId = 'u1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Ann", c.getString(0))
        }

        // The new table exists and is empty.
        db.query("SELECT count(*) FROM vitals_samples").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("vitals_samples should be empty after migration", 0, c.getInt(0))
        }

        // Row A: every nullable vitals column populated — proves the REAL/INTEGER affinities round-trip.
        db.execSQL(
            "INSERT INTO vitals_samples " +
                "(id, userId, localDate, timestampUtcEpochMs, tzOffsetMinutes, kind, restingHeartRate, " +
                "hrvRmssd, respiratoryRate, bodyTemperature, hydrationMl, spo2, sleepDurationMinutes, " +
                "deepSleepMinutes, updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, isSynced) " +
                "VALUES ('u1|rollup|2026-09-30', 'u1', '2026-09-30', 1000, 0, 'rollup', 58.0, " +
                "65.5, 14.2, 36.6, 750.0, 98.0, 420, 90, 1000, 0, NULL, 0)"
        )
        db.query(
            "SELECT kind, restingHeartRate, hrvRmssd, respiratoryRate, bodyTemperature, hydrationMl, " +
                "spo2, sleepDurationMinutes, deepSleepMinutes FROM vitals_samples " +
                "WHERE id = 'u1|rollup|2026-09-30'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("rollup", c.getString(0))
            assertEquals(58.0, c.getDouble(1), 0.0001)
            assertEquals(65.5, c.getDouble(2), 0.0001)
            assertEquals(14.2, c.getDouble(3), 0.0001)
            assertEquals(36.6, c.getDouble(4), 0.0001)
            assertEquals(750.0, c.getDouble(5), 0.0001)
            assertEquals(98.0, c.getDouble(6), 0.0001)
            assertEquals(420, c.getInt(7))
            assertEquals(90, c.getInt(8))
        }

        // Row B: every nullable vitals column left NULL — proves the nullable affinities round-trip both ways.
        db.execSQL(
            "INSERT INTO vitals_samples " +
                "(id, userId, localDate, timestampUtcEpochMs, tzOffsetMinutes, kind, " +
                "updatedAtEpochMs, updatedAtTzOffsetMinutes, deletedAtEpochMs, isSynced) " +
                "VALUES ('u1|sample|2000|sample', 'u1', '2026-09-30', 2000, 0, 'sample', 2000, 0, NULL, 0)"
        )
        db.query(
            "SELECT restingHeartRate, hrvRmssd, respiratoryRate, bodyTemperature, hydrationMl, " +
                "spo2, sleepDurationMinutes, deepSleepMinutes FROM vitals_samples " +
                "WHERE id = 'u1|sample|2000|sample'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("restingHeartRate should be null", c.isNull(0))
            assertTrue("hrvRmssd should be null", c.isNull(1))
            assertTrue("respiratoryRate should be null", c.isNull(2))
            assertTrue("bodyTemperature should be null", c.isNull(3))
            assertTrue("hydrationMl should be null", c.isNull(4))
            assertTrue("spo2 should be null", c.isNull(5))
            assertTrue("sleepDurationMinutes should be null", c.isNull(6))
            assertTrue("deepSleepMinutes should be null", c.isNull(7))
        }
        db.close()
    }
}
