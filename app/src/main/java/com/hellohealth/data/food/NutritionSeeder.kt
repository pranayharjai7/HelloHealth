package com.hellohealth.data.food

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.domain.repository.NutritionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-call, idempotent entry point for populating the read-only common-foods catalog, invoked from
 * `MainActivity.onCreate`. Delegates to [NutritionRepository.seedCatalogIfEmpty], which is
 * count-gated and IO-safe.
 *
 * Modeled on [com.hellohealth.data.exercise.ExerciseSeeder]: running every launch and seeding only
 * when the cache is empty covers BOTH fresh installs and users migrated up to v11 (whose DB already
 * existed, so a `RoomDatabase.Callback.onCreate` would miss them), and self-heals a prior partial
 * seed. Never throws — a catalog problem can't crash app start.
 */
@Singleton
class NutritionSeeder @Inject constructor(
    private val nutritionRepository: NutritionRepository,
) {
    suspend fun seedIfNeeded() {
        runCatching { nutritionRepository.seedCatalogIfEmpty() }
            .onFailure { t ->
                AppLogger.e(FeatureTag.NUTRITION, "seedIfNeeded failed; food catalog may be empty", t)
            }
    }
}
