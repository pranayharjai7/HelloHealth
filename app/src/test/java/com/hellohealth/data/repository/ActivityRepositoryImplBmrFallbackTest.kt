package com.hellohealth.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.data.health.HealthConnectManager
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.domain.health.BodyEnergy
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * P0.5 Step 10d — the full [ActivityRepositoryImpl.resolveBmrFallback] read path (not just the pure
 * companion selector): a complete profile yields its Mifflin-St Jeor BMR, and — the branch no pure
 * test can reach — a profile read that THROWS degrades to the 1800.0 default rather than propagating.
 *
 * Robolectric because [HealthConnectManager] touches Android APIs at construction.
 */
@RunWith(RobolectricTestRunner::class)
class ActivityRepositoryImplBmrFallbackTest {

    private lateinit var db: AppDatabase

    private class FakeProfileRepository(
        var profile: UserProfile?,
        var throwOnRead: Boolean = false
    ) : ProfileRepository {
        override suspend fun getProfile(): UserProfile? {
            if (throwOnRead) error("profile read failed")
            return profile
        }
        override suspend fun upsertProfile(profile: UserProfile) { this.profile = profile }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(profileRepo: ProfileRepository): ActivityRepositoryImpl {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return ActivityRepositoryImpl(
            healthConnectManager = HealthConnectManager(null, ctx),
            snapshotDao = db.snapshotDao(),
            sessionManager = object : SupabaseSessionManager(null) {
                override suspend fun getCurrentUserId(): String? = "u1"
            },
            syncScheduler = object : SyncScheduler(ctx) {
                override fun requestSync() {}
            },
            profileRepository = profileRepo
        )
    }

    private fun birthEpochDayForAge(age: Int, today: LocalDate = LocalDate.now()): Long =
        today.minusYears(age.toLong()).toEpochDay()

    @Test
    fun `resolveBmrFallback derives the profile BMR when vitals are present`() = runTest {
        val profile = UserProfile(
            weightKg = 80.0,
            heightCm = 180.0,
            birthDateEpochDay = birthEpochDayForAge(30),
            gender = Gender.MALE
        )
        val expected = BodyEnergy.bmr(80.0, 180.0, 30, Gender.MALE)!! // 1780.0
        assertEquals(expected, repo(FakeProfileRepository(profile)).resolveBmrFallback(), 0.0001)
    }

    @Test
    fun `resolveBmrFallback returns the default when there is no profile`() = runTest {
        assertEquals(
            ActivityRepositoryImpl.DEFAULT_BMR,
            repo(FakeProfileRepository(null)).resolveBmrFallback(),
            0.0001
        )
    }

    @Test
    fun `resolveBmrFallback degrades to the default when the profile read throws`() = runTest {
        // The defined error path: a throwing getProfile() must be caught and yield 1800.0, never
        // propagate. If the runCatching in resolveBmrFallback were removed, this test fails.
        val throwing = FakeProfileRepository(profile = null, throwOnRead = true)
        assertEquals(
            ActivityRepositoryImpl.DEFAULT_BMR,
            repo(throwing).resolveBmrFallback(),
            0.0001
        )
    }
}
