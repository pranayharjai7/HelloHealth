package com.hellohealth.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.CachedFoodDao
import com.hellohealth.data.local.dao.NutritionEntryDao
import com.hellohealth.data.local.entities.CachedFoodEntity
import com.hellohealth.data.food.FoodCatalogAssetLoader
import com.hellohealth.domain.model.nutrition.MealCategory
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NutritionRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var entryDao: NutritionEntryDao
    private lateinit var foodDao: CachedFoodDao
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
        entryDao = db.nutritionEntryDao()
        foodDao = db.cachedFoodDao()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(
        userId: String?,
        catalogResult: Result<List<CachedFoodEntity>> = Result.success(emptyList()),
    ) = NutritionRepositoryImpl(
        nutritionEntryDao = entryDao,
        cachedFoodDao = foodDao,
        catalogAssetLoader = object : FoodCatalogAssetLoader(
            ApplicationProvider.getApplicationContext<Context>()
        ) {
            override fun load(nowEpochMs: Long): Result<List<CachedFoodEntity>> = catalogResult
        },
        sessionManager = object : SupabaseSessionManager(null) {
            override suspend fun getCurrentUserId(): String? = userId
        },
        syncScheduler = syncScheduler,
    )

    private val today = "2026-09-30"

    @Test
    fun `no user reads empty summary and drops writes without scheduling sync`() = runTest {
        val repo = repo(null)

        val summary = repo.observeDaySummary(today).first()
        assertEquals(0.0, summary.caloriesConsumed, 0.0001)
        assertTrue(summary.entriesByMeal.isEmpty())
        assertTrue(repo.observeEntries(today).first().isEmpty())

        repo.addQuickAdd(today, MealCategory.LUNCH, "Sandwich", 1.0, "serving", 350.0)
        repo.addWater(today)
        assertEquals(0, syncRequests)
        assertTrue(entryDao.getUnsynced().isEmpty())
    }

    @Test
    fun `quick-add and water sum into the day summary`() = runTest {
        val repo = repo("u1")

        repo.addQuickAdd(
            localDate = today, mealCategory = MealCategory.BREAKFAST, foodName = "Oats",
            quantity = 100.0, unit = "g", calories = 389.0,
            proteinG = 16.9, carbsG = 66.3, fatG = 6.9, fibreG = 10.6,
        )
        repo.addQuickAdd(today, MealCategory.LUNCH, "Sandwich", 1.0, "serving", 350.0)
        repo.addWater(today, 250.0)
        repo.addWater(today, 250.0)

        val summary = repo.observeDaySummary(today).first()
        assertEquals(739.0, summary.caloriesConsumed, 0.0001)
        assertEquals(16.9, summary.proteinG, 0.0001)
        assertEquals(500.0, summary.waterMl, 0.0001)
        // Two meal sections, each with one entry, ordered breakfast before lunch.
        assertEquals(
            listOf(MealCategory.BREAKFAST, MealCategory.LUNCH),
            summary.entriesByMeal.keys.toList()
        )
        assertEquals(4, syncRequests)
    }

    @Test
    fun `addFromFood scales per-100g catalog macros to the logged grams`() = runTest {
        foodDao.upsert(
            CachedFoodEntity(
                id = "seed:oats", name = "Oats", brand = null, source = "seed",
                basisUnit = "per_100g", servingLabel = null, servingGrams = null,
                caloriesPer = 389.0, proteinGPer = 16.9, carbsGPer = 66.3,
                fatGPer = 6.9, fibreGPer = 10.6, barcode = null, lastRefreshedEpochMs = 1L,
            )
        )
        val repo = repo("u1")

        repo.addFromFood(today, MealCategory.BREAKFAST, "seed:oats", quantity = 50.0, unit = "g")

        val entry = repo.observeEntries(today).first().single()
        // 50 g = half of 100 g basis.
        assertEquals(194.5, entry.calories, 0.0001)
        assertEquals(8.45, entry.proteinG!!, 0.0001)
        assertEquals("Oats", entry.foodName)
    }

    @Test
    fun `addFromFood with an unknown food id is a no-op`() = runTest {
        val repo = repo("u1")
        repo.addFromFood(today, MealCategory.DINNER, "usda:does-not-exist", 100.0, "g")
        assertTrue(repo.observeEntries(today).first().isEmpty())
        assertEquals(0, syncRequests)
    }

    @Test
    fun `soft-delete removes the entry from reads but keeps a syncing tombstone`() = runTest {
        val repo = repo("u1")
        repo.addQuickAdd(today, MealCategory.LUNCH, "Sandwich", 1.0, "serving", 350.0)

        val entry = repo.observeEntries(today).first().single()
        repo.deleteEntry(entry.id)

        assertTrue(repo.observeEntries(today).first().isEmpty())
        // The tombstone is still an unsynced row (so the delete propagates) with deletedAtEpochMs set.
        val unsynced = entryDao.getUnsynced()
        assertEquals(1, unsynced.size)
        assertEquals(entry.id, unsynced.single().id)
        assertTrue(entryDao.getById(entry.id)!!.deletedAtEpochMs != null)
    }

    @Test
    fun `search returns cached matches and is blank-safe`() = runTest {
        foodDao.upsert(
            CachedFoodEntity(
                id = "seed:banana", name = "Banana", brand = null, source = "seed",
                basisUnit = "per_100g", caloriesPer = 89.0, lastRefreshedEpochMs = 1L,
            )
        )
        val repo = repo("u1")

        assertTrue(repo.searchFoods("   ").isEmpty())
        val hits = repo.searchFoods("ban")
        assertEquals(1, hits.size)
        assertEquals("Banana", hits.single().name)
    }

    @Test
    fun `resolveBarcode reads the cache and is blank-safe`() = runTest {
        foodDao.upsert(
            CachedFoodEntity(
                id = "off:123", name = "Cola", brand = "BrandCo", source = "off",
                basisUnit = "per_100g", caloriesPer = 42.0, barcode = "123",
                lastRefreshedEpochMs = 1L,
            )
        )
        val repo = repo("u1")

        assertNull(repo.resolveBarcode(""))
        assertNull(repo.resolveBarcode("999"))
        assertEquals("Cola", repo.resolveBarcode("123")!!.name)
    }

    @Test
    fun `seedCatalogIfEmpty inserts once and is idempotent`() = runTest {
        val seed = listOf(
            CachedFoodEntity(
                id = "seed:banana", name = "Banana", source = "seed",
                basisUnit = "per_100g", caloriesPer = 89.0, lastRefreshedEpochMs = 1L,
            )
        )
        val repo = repo("u1", catalogResult = Result.success(seed))

        repo.seedCatalogIfEmpty()
        assertEquals(1, foodDao.count())

        // Second call is count-gated → no duplicate insert (still one row).
        repo.seedCatalogIfEmpty()
        assertEquals(1, foodDao.count())
    }

    @Test
    fun `seedCatalogIfEmpty leaves the cache empty when the asset fails to load`() = runTest {
        val repo = repo("u1", catalogResult = Result.failure(RuntimeException("bad asset")))
        repo.seedCatalogIfEmpty()
        assertEquals(0, foodDao.count())
    }
}
