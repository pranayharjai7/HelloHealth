package com.hellohealth.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.ProfileDao
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var profileDao: ProfileDao
    private var syncRequests = 0

    private val syncScheduler = object : SyncScheduler(
        ApplicationProvider.getApplicationContext<Context>()
    ) {
        override fun requestSync() { syncRequests++ }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        profileDao = db.profileDao()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(userId: String?) = ProfileRepositoryImpl(
        profileDao = profileDao,
        sessionManager = object : SupabaseSessionManager(null) {
            override suspend fun getCurrentUserId(): String? = userId
        },
        syncScheduler = syncScheduler
    )

    @Test
    fun `no user returns null profile and drops writes`() = runTest {
        val repo = repo(null)
        assertNull(repo.getProfile())

        repo.upsertProfile(UserProfile(displayName = "Ann"))
        assertEquals(0, syncRequests)
        assertTrue(profileDao.getUnsynced().isEmpty())
    }

    @Test
    fun `upsert writes unsynced row, reads back, requests sync`() = runTest {
        val repo = repo("u1")

        repo.upsertProfile(UserProfile(displayName = "Ann"))

        assertEquals("Ann", repo.getProfile()?.displayName)
        val unsynced = profileDao.getUnsynced()
        assertEquals(1, unsynced.size)
        assertFalse(unsynced[0].isSynced)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `blank display name is normalized to null`() = runTest {
        val repo = repo("u1")
        repo.upsertProfile(UserProfile(displayName = "   "))
        assertNull(repo.getProfile()?.displayName)
    }

    @Test
    fun `vitals round-trip through Room including enums and hasOnboarded`() = runTest {
        val repo = repo("u1")
        val born = java.time.LocalDate.of(1990, 5, 20).toEpochDay()
        val profile = UserProfile(
            displayName = "Ann",
            gender = com.hellohealth.domain.model.Gender.FEMALE,
            birthDateEpochDay = born,
            heightCm = 168.0,
            weightKg = 62.5,
            activityLevel = com.hellohealth.domain.model.ActivityLevel.MODERATE,
            goalType = com.hellohealth.domain.model.GoalType.LOSE,
            targetWeightKg = 58.0,
            targetRateKgPerWeek = 0.5,
            unitPreference = com.hellohealth.domain.model.UnitPreference.IMPERIAL,
            hasOnboarded = true
        )

        repo.upsertProfile(profile)
        val readBack = repo.getProfile()!!

        assertEquals(profile, readBack)
    }

    @Test
    fun `unknown stored enum degrades to null rather than crashing`() = runTest {
        // Simulate a corrupt/forward-compat enum value written directly to Room.
        profileDao.upsert(
            com.hellohealth.data.local.entities.ProfileEntity(
                userId = "u1",
                displayName = "Ann",
                updatedAtEpochMs = 1L,
                updatedAtTzOffsetMinutes = 0,
                gender = "MARTIAN",
                activityLevel = "HYPERSONIC"
            )
        )
        val readBack = repo("u1").getProfile()!!
        assertNull(readBack.gender)
        assertNull(readBack.activityLevel)
    }
}
