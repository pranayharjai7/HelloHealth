package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.data.exercise.ExerciseAssetLoader
import com.hellohealth.data.local.dao.ExerciseDao
import com.hellohealth.domain.model.Exercise
import com.hellohealth.domain.repository.ExerciseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ExerciseRepository] over Room ([ExerciseDao]) + the bundled asset ([ExerciseAssetLoader]).
 *
 * Local-only — no [com.hellohealth.data.auth.SupabaseSessionManager] / syncScheduler (contrast the
 * user repositories): the catalog is global, read-only and identical on every device, so there is
 * nothing to gate by user or push to Supabase. All DB work runs on [Dispatchers.IO].
 *
 * [seedIfEmpty] is count-gated and idempotent: it inserts only when the table is empty, and a parse
 * failure leaves the table empty (count stays 0) so the next launch retries — the picker just shows
 * an empty state meanwhile. `insertAll` REPLACE self-heals a partial insert.
 */
@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val assetLoader: ExerciseAssetLoader,
) : ExerciseRepository {

    override suspend fun seedIfEmpty(): Int = withContext(Dispatchers.IO) {
        val existing = exerciseDao.count()
        if (existing > 0) return@withContext existing

        assetLoader.loadExercises()
            .onSuccess { entities ->
                if (entities.isEmpty()) {
                    AppLogger.w(FeatureTag.EXERCISE_CATALOG, "asset parsed to 0 exercises; nothing seeded")
                } else {
                    exerciseDao.insertAll(entities)
                    AppLogger.i(FeatureTag.EXERCISE_CATALOG, "seeded ${entities.size} exercises")
                }
            }
        // Re-read so callers see the true post-seed count (0 if the load failed → retry next launch).
        exerciseDao.count()
    }

    override suspend fun count(): Int = withContext(Dispatchers.IO) { exerciseDao.count() }

    override suspend fun search(query: String, limit: Int): List<Exercise> =
        withContext(Dispatchers.IO) {
            // Escape LIKE wildcards in user input, then wrap; a blank query becomes "%%" (matches all).
            val like = "%" + query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            exerciseDao.search(like, limit).map { it.toDomain() }
        }

    override suspend fun getById(id: String): Exercise? =
        withContext(Dispatchers.IO) { exerciseDao.getById(id)?.toDomain() }

    override suspend fun getByIds(ids: Collection<String>): Map<String, Exercise> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyMap()
            exerciseDao.getByIds(ids.distinct()).associate { it.id to it.toDomain() }
        }

    override suspend fun getAllCategories(): List<String> =
        withContext(Dispatchers.IO) { exerciseDao.getAllCategories() }
}
