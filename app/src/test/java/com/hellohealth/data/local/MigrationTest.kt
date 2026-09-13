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
}
