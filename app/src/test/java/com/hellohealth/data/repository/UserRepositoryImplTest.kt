package com.hellohealth.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.FoodPrefsDao
import com.hellohealth.domain.model.FoodPreferences
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var foodPrefsDao: FoodPrefsDao
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
        foodPrefsDao = db.foodPrefsDao()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(userId: String?) = UserRepositoryImpl(
        foodPrefsDao = foodPrefsDao,
        sessionManager = object : SupabaseSessionManager(null) {
            override suspend fun getCurrentUserId(): String? = userId
        },
        syncScheduler = syncScheduler
    )

    @Test
    fun `no user reads defaults and drops writes`() = runTest {
        val repo = repo(null)
        assertEquals(FoodPreferences(), repo.getCurrentFoodPreferences())
        assertEquals(FoodPreferences(), repo.getFoodPreferences().first())

        repo.updateFoodPreferences(FoodPreferences(dietType = "vegan"))
        assertEquals(0, syncRequests)
        assertTrue(foodPrefsDao.getUnsynced().isEmpty())
    }

    @Test
    fun `update persists list columns via converters and reads back`() = runTest {
        val repo = repo("u1")
        val prefs = FoodPreferences(
            dietType = "vegan",
            allergies = listOf("dairy", "nuts"),
            cuisinePreferences = listOf("Indian", "Asian")
        )

        repo.updateFoodPreferences(prefs)

        // List columns round-trip through the Room type converters.
        assertEquals(prefs, repo.getCurrentFoodPreferences())
        val unsynced = foodPrefsDao.getUnsynced()
        assertEquals(1, unsynced.size)
        assertFalse(unsynced[0].isSynced)
        assertEquals(listOf("dairy", "nuts"), unsynced[0].allergies)
        assertEquals(1, syncRequests)
    }
}
