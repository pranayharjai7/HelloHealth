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

        // Apply MIGRATION_7_8 and validate the resulting schema matches 8.json exactly.
        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

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
}
