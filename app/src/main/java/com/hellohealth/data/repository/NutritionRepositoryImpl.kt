package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.CachedFoodDao
import com.hellohealth.data.local.dao.NutritionEntryDao
import com.hellohealth.data.local.entities.CachedFoodEntity
import com.hellohealth.data.local.entities.NutritionEntryEntity
import com.hellohealth.domain.model.nutrition.FoodEntry
import com.hellohealth.domain.model.nutrition.MealCategory
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first nutrition repository. Food + water writes hit Room with `isSynced=false` then poke
 * [SyncScheduler]; [com.hellohealth.sync.NutritionEntrySyncer] owns the Supabase round-trip. Reads
 * are Room Flows aggregated into a [NutritionDaySummary] at read time (no denormalized summary).
 *
 * With no signed-in user, reads emit [NutritionDaySummary.empty]/empty-list and writes are dropped
 * with a warning — matching the VitalsRepositoryImpl / EmotionsRepositoryImpl contract so screens
 * never special-case a missing session.
 *
 * The `cached_foods` catalog is per-device and un-synced (like `exercises`): the bundled-asset seed
 * ([seedCatalogIfEmpty]) and the remote (USDA/Open Food Facts) augmentation of
 * [searchFoods]/[resolveBarcode] are layered on in later phases; the Room paths here always work
 * offline and never throw.
 */
@Singleton
class NutritionRepositoryImpl @Inject constructor(
    private val nutritionEntryDao: NutritionEntryDao,
    private val cachedFoodDao: CachedFoodDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler,
) : NutritionRepository {

    override fun observeDaySummary(localDate: String): Flow<NutritionDaySummary> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(NutritionDaySummary.empty(localDate))
            return@flow
        }
        val foodFlow = nutritionEntryDao.observeFoodForDate(userId, localDate)
        val waterFlow = nutritionEntryDao.observeWaterForDate(userId, localDate)
        emitAll(
            combine(foodFlow, waterFlow) { foodRows, waterRows ->
                buildSummary(localDate, foodRows, waterRows)
            }
        )
    }.flowOn(Dispatchers.IO)

    override fun observeEntries(localDate: String): Flow<List<FoodEntry>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            nutritionEntryDao.observeFoodForDate(userId, localDate)
                .map { rows -> rows.map { it.toFoodEntry() } }
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun addQuickAdd(
        localDate: String,
        mealCategory: MealCategory,
        foodName: String,
        quantity: Double,
        unit: String,
        calories: Double,
        proteinG: Double?,
        carbsG: Double?,
        fatG: Double?,
        fibreG: Double?,
    ) {
        val userId = requireUser("addQuickAdd") ?: return
        upsertFood(
            userId = userId,
            localDate = localDate,
            mealCategory = mealCategory,
            foodId = null,
            foodName = foodName,
            quantity = quantity,
            unit = unit,
            calories = calories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            fibreG = fibreG,
            entryMethod = ENTRY_QUICK_ADD,
        )
    }

    override suspend fun addFromFood(
        localDate: String,
        mealCategory: MealCategory,
        foodId: String,
        quantity: Double,
        unit: String,
    ) {
        val userId = requireUser("addFromFood") ?: return
        val food = cachedFoodDao.getById(foodId)
        if (food == null) {
            AppLogger.w(FeatureTag.NUTRITION, "addFromFood: food id $foodId not in cache; dropping write")
            return
        }
        val scaled = scaleMacros(food, quantity)
        upsertFood(
            userId = userId,
            localDate = localDate,
            mealCategory = mealCategory,
            foodId = foodId,
            foodName = food.name,
            quantity = quantity,
            unit = unit,
            calories = scaled.calories,
            proteinG = scaled.proteinG,
            carbsG = scaled.carbsG,
            fatG = scaled.fatG,
            fibreG = scaled.fibreG,
            entryMethod = ENTRY_CATALOG,
        )
    }

    override suspend fun addWater(localDate: String, waterMl: Double) {
        val userId = requireUser("addWater") ?: return
        val nowMs = Timestamps.nowEpochMs()
        nutritionEntryDao.upsert(
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                localDate = localDate,
                timestampUtcEpochMs = nowMs,
                tzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                kind = KIND_WATER,
                mealCategory = null,
                foodId = null,
                foodName = WATER_NAME,
                quantity = 1.0,
                unit = UNIT_GLASS,
                calories = 0.0,
                proteinG = null,
                carbsG = null,
                fatG = null,
                fibreG = null,
                waterMl = waterMl,
                entryMethod = ENTRY_WATER,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = null,
                isSynced = false,
            )
        )
        AppLogger.d(FeatureTag.NUTRITION, "water $waterMl ml logged for $localDate; requesting sync")
        syncScheduler.requestSync()
    }

    override suspend fun deleteEntry(id: String) {
        requireUser("deleteEntry") ?: return
        val existing = nutritionEntryDao.getById(id) ?: run {
            AppLogger.w(FeatureTag.NUTRITION, "deleteEntry: id $id not found; no-op")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        // Soft-delete: stamp a tombstone and re-mark unsynced so the delete propagates.
        nutritionEntryDao.upsert(
            existing.copy(
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = nowMs,
                isSynced = false,
            )
        )
        AppLogger.d(FeatureTag.NUTRITION, "entry $id soft-deleted; requesting sync")
        syncScheduler.requestSync()
    }

    override suspend fun searchFoods(query: String): List<CachedFoodEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        // Local cache only for now; the remote (USDA/OFF) augmentation is layered in later. Never throws.
        return runCatching {
            cachedFoodDao.search(buildLikePattern(trimmed), SEARCH_LIMIT)
        }.getOrElse {
            AppLogger.w(FeatureTag.NUTRITION, "searchFoods failed: ${it.message}")
            emptyList()
        }
    }

    override suspend fun resolveBarcode(barcode: String): CachedFoodEntity? {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return null
        // Local cache only for now; Open Food Facts lookup on a miss is layered in later. Never throws.
        return runCatching { cachedFoodDao.getByBarcode(trimmed) }.getOrElse {
            AppLogger.w(FeatureTag.NUTRITION, "resolveBarcode failed: ${it.message}")
            null
        }
    }

    override suspend fun seedCatalogIfEmpty() {
        // Count-gated bundled-asset seed is wired in the catalog phase; the Room cache simply stays
        // empty until then, and search/barcode + quick-add still work. Never throws.
        val existing = runCatching { cachedFoodDao.count() }.getOrDefault(0)
        if (existing > 0) return
        AppLogger.d(FeatureTag.NUTRITION, "catalog empty; bundled seed pending catalog phase")
    }

    // --- helpers ---

    private suspend fun requireUser(op: String): String? {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.NUTRITION, "$op with no signed-in user; dropping write")
        }
        return userId
    }

    private suspend fun upsertFood(
        userId: String,
        localDate: String,
        mealCategory: MealCategory,
        foodId: String?,
        foodName: String,
        quantity: Double,
        unit: String,
        calories: Double,
        proteinG: Double?,
        carbsG: Double?,
        fatG: Double?,
        fibreG: Double?,
        entryMethod: String,
    ) {
        val nowMs = Timestamps.nowEpochMs()
        nutritionEntryDao.upsert(
            NutritionEntryEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                localDate = localDate,
                timestampUtcEpochMs = nowMs,
                tzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                kind = KIND_FOOD,
                mealCategory = mealCategory.wire,
                foodId = foodId,
                foodName = foodName,
                quantity = quantity,
                unit = unit,
                calories = calories,
                proteinG = proteinG,
                carbsG = carbsG,
                fatG = fatG,
                fibreG = fibreG,
                waterMl = null,
                entryMethod = entryMethod,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = null,
                isSynced = false,
            )
        )
        AppLogger.d(FeatureTag.NUTRITION, "food '$foodName' logged for $localDate; requesting sync")
        syncScheduler.requestSync()
    }

    private data class ScaledMacros(
        val calories: Double,
        val proteinG: Double?,
        val carbsG: Double?,
        val fatG: Double?,
        val fibreG: Double?,
    )

    /**
     * Scale a catalog food's per-basis facts to the logged [quantity]. For a `per_100g` basis the
     * quantity is grams (factor = grams/100); for a `per_serving` basis the quantity is a serving
     * count (factor = quantity). Null per-basis macros stay null.
     */
    private fun scaleMacros(food: CachedFoodEntity, quantity: Double): ScaledMacros {
        val factor = when (food.basisUnit) {
            BASIS_PER_100G -> quantity / 100.0
            else -> quantity // per_serving (or unknown → treat quantity as the multiplier)
        }
        fun scale(value: Double?) = value?.let { it * factor }
        return ScaledMacros(
            calories = food.caloriesPer * factor,
            proteinG = scale(food.proteinGPer),
            carbsG = scale(food.carbsGPer),
            fatG = scale(food.fatGPer),
            fibreG = scale(food.fibreGPer),
        )
    }

    private fun buildSummary(
        localDate: String,
        foodRows: List<NutritionEntryEntity>,
        waterRows: List<NutritionEntryEntity>,
    ): NutritionDaySummary {
        val entries = foodRows.map { it.toFoodEntry() }
        val byMeal = entries
            .filter { it.mealCategory != null }
            .groupBy { it.mealCategory!! }
            .toSortedMap(compareBy { it.order })
        return NutritionDaySummary(
            localDate = localDate,
            caloriesConsumed = entries.sumOf { it.calories },
            proteinG = entries.sumOf { it.proteinG ?: 0.0 },
            carbsG = entries.sumOf { it.carbsG ?: 0.0 },
            fatG = entries.sumOf { it.fatG ?: 0.0 },
            fibreG = entries.sumOf { it.fibreG ?: 0.0 },
            waterMl = waterRows.sumOf { it.waterMl ?: 0.0 },
            entriesByMeal = byMeal,
        )
    }

    private fun NutritionEntryEntity.toFoodEntry() = FoodEntry(
        id = id,
        mealCategory = MealCategory.fromWire(mealCategory),
        foodName = foodName,
        quantity = quantity,
        unit = unit,
        calories = calories,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        fibreG = fibreG,
        timestampUtcEpochMs = timestampUtcEpochMs,
    )

    /** Wrap user text as a case-insensitive `%…%` LIKE pattern, escaping literal wildcards. */
    private fun buildLikePattern(query: String): String {
        val escaped = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    companion object {
        private const val KIND_FOOD = "food"
        private const val KIND_WATER = "water"
        private const val ENTRY_QUICK_ADD = "quick_add"
        private const val ENTRY_CATALOG = "catalog"
        private const val ENTRY_WATER = "water"
        private const val WATER_NAME = "Water"
        private const val UNIT_GLASS = "glass"
        private const val BASIS_PER_100G = "per_100g"
        private const val SEARCH_LIMIT = 25
    }
}
