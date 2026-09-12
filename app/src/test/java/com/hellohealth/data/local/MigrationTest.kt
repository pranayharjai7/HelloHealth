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
}
