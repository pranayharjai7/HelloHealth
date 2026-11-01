package com.hellohealth.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.data.exercise.ExerciseSeeder
import com.hellohealth.data.food.NutritionSeeder
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.entities.CachedFoodEntity
import com.hellohealth.data.local.entities.GoalsEntity
import com.hellohealth.data.local.entities.UserEntity
import com.hellohealth.domain.model.Exercise
import com.hellohealth.domain.model.nutrition.FoodEntry
import com.hellohealth.domain.model.nutrition.MealCategory
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import com.hellohealth.domain.repository.ExerciseRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.sync.SyncScheduler
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.MemoryCodeVerifierCache
import io.github.jan.supabase.gotrue.MemorySessionManager
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.user.UserSession
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var syncScheduler: FakeSyncScheduler

    class FakeSyncScheduler : SyncScheduler(ApplicationProvider.getApplicationContext()) {
        var cancelAllCalled: Boolean = false
        override fun cancelAll() {
            cancelAllCalled = true
        }
    }

    private class FakeExerciseRepo : ExerciseRepository {
        var seedCalled = false
        override suspend fun seedIfEmpty(): Int {
            seedCalled = true
            return 1
        }
        override suspend fun count(): Int = 1
        override suspend fun search(query: String, limit: Int): List<Exercise> = emptyList()
        override suspend fun getById(id: String): Exercise? = null
        override suspend fun getByIds(ids: Collection<String>): Map<String, Exercise> = emptyMap()
        override suspend fun getAllCategories(): List<String> = emptyList()
    }

    private class FakeNutritionRepo : NutritionRepository {
        var seedCalled = false
        override fun observeDaySummary(localDate: String): Flow<NutritionDaySummary> = flowOf(NutritionDaySummary.empty(localDate))
        override fun observeEntries(localDate: String): Flow<List<FoodEntry>> = flowOf(emptyList())
        override suspend fun addQuickAdd(localDate: String, mealCategory: MealCategory, foodName: String, quantity: Double, unit: String, calories: Double, proteinG: Double?, carbsG: Double?, fatG: Double?, fibreG: Double?) = Unit
        override suspend fun addFromFood(localDate: String, mealCategory: MealCategory, foodId: String, quantity: Double, unit: String) = Unit
        override suspend fun addWater(localDate: String, waterMl: Double) = Unit
        override suspend fun deleteEntry(id: String) = Unit
        override suspend fun searchFoods(query: String): List<CachedFoodEntity> = emptyList()
        override suspend fun resolveBarcode(barcode: String): CachedFoodEntity? = null
        override suspend fun seedCatalogIfEmpty() {
            seedCalled = true
        }
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        syncScheduler = FakeSyncScheduler()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `deleteAccount returns failure when no user session is present`() = runTest {
        val supabase = createSupabaseClient("https://test.supabase.co", "anon") {
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
            }
            install(Functions)
        }
        val exerciseRepo = FakeExerciseRepo()
        val nutritionRepo = FakeNutritionRepo()
        val repo = AuthRepositoryImpl(
            supabase = supabase,
            database = db,
            syncScheduler = syncScheduler,
            exerciseSeeder = ExerciseSeeder(exerciseRepo),
            nutritionSeeder = NutritionSeeder(nutritionRepo),
            userDao = db.userDao(),
        )

        val result = repo.deleteAccount()
        assertTrue("Deletion must fail when user is not authenticated", result.isFailure)
        assertEquals("No authenticated user to delete", result.exceptionOrNull()?.message)
        assertFalse("syncScheduler.cancelAll should not be called", syncScheduler.cancelAllCalled)
    }

    @Test
    fun `deleteAccount returns failure when server edge function returns non-2xx`() = runTest {
        val engine = MockEngine { request ->
            respondError(HttpStatusCode.InternalServerError)
        }
        val supabase = createSupabaseClient("https://test.supabase.co", "anon") {
            httpEngine = engine
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
            }
            install(Functions)
        }
        supabase.auth.importSession(
            UserSession(
                accessToken = "test-token",
                refreshToken = "test-refresh",
                expiresIn = 3600,
                tokenType = "bearer",
                user = null
            )
        )

        // Seed some data in local DB
        db.userDao().insertUser(UserEntity(email = "test@example.com", password = "secret_password", isLoggedIn = true))
        db.goalsDao().upsert(
            GoalsEntity(
                userId = "test-user-id",
                steps = 10000,
                activeCalories = 500,
                activeMinutes = 30,
                updatedAtEpochMs = System.currentTimeMillis(),
                updatedAtTzOffsetMinutes = 0
            )
        )

        val exerciseRepo = FakeExerciseRepo()
        val nutritionRepo = FakeNutritionRepo()
        val repo = AuthRepositoryImpl(
            supabase = supabase,
            database = db,
            syncScheduler = syncScheduler,
            exerciseSeeder = ExerciseSeeder(exerciseRepo),
            nutritionSeeder = NutritionSeeder(nutritionRepo),
            userDao = db.userDao(),
        )

        val result = repo.deleteAccount()
        assertTrue("Deletion must fail on server error", result.isFailure)
        assertNotNull("Exception must be present on server error", result.exceptionOrNull())

        // Local data must NOT be cleared when server deletion fails
        assertNotNull(db.userDao().getUserByEmail("test@example.com"))
        assertFalse("SyncScheduler must not be cancelled on failed deletion", syncScheduler.cancelAllCalled)
    }

    @Test
    fun `deleteAccount clears local database, logs out, and cancels sync on server success`() = runTest {
        var functionCalled = false
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("delete-account")) {
                functionCalled = true
                respond(
                    content = """{"success":true,"message":"Account deleted"}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
            } else {
                respondError(HttpStatusCode.NotFound)
            }
        }
        val supabase = createSupabaseClient("https://test.supabase.co", "anon") {
            httpEngine = engine
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
            }
            install(Functions)
        }
        supabase.auth.importSession(
            UserSession(
                accessToken = "test-token",
                refreshToken = "test-refresh",
                expiresIn = 3600,
                tokenType = "bearer",
                user = null
            )
        )

        // Pre-populate local DB
        db.userDao().insertUser(UserEntity(email = "test@example.com", password = "secret_password", isLoggedIn = true))
        db.goalsDao().upsert(
            GoalsEntity(
                userId = "test-user-id",
                steps = 10000,
                activeCalories = 500,
                activeMinutes = 30,
                updatedAtEpochMs = System.currentTimeMillis(),
                updatedAtTzOffsetMinutes = 0
            )
        )

        val exerciseRepo = FakeExerciseRepo()
        val nutritionRepo = FakeNutritionRepo()
        val repo = AuthRepositoryImpl(
            supabase = supabase,
            database = db,
            syncScheduler = syncScheduler,
            exerciseSeeder = ExerciseSeeder(exerciseRepo),
            nutritionSeeder = NutritionSeeder(nutritionRepo),
            userDao = db.userDao(),
        )

        val result = repo.deleteAccount()
        assertTrue("Deletion must succeed when server returns 200", result.isSuccess)
        assertTrue("Edge function delete-account must have been invoked", functionCalled)
        assertTrue("Sync scheduler cancelAll must be invoked", syncScheduler.cancelAllCalled)
        assertTrue("Exercise seeder must re-seed catalog", exerciseRepo.seedCalled)
        assertTrue("Nutrition seeder must re-seed catalog", nutritionRepo.seedCalled)

        // Local user-specific data must be completely cleared from local database
        assertNull("User data in database must be wiped", db.userDao().getUserByEmail("test@example.com"))
    }
}
