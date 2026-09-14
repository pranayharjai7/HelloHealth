package com.hellohealth.data.exercise

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.domain.repository.ExerciseRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-call, idempotent entry point for populating the exercise catalog, invoked from
 * `MainActivity.onCreate` (Step 7). Delegates to [ExerciseRepository.seedIfEmpty], which is
 * count-gated on [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO].
 *
 * Triggering from Activity.onCreate (rather than a `RoomDatabase.Callback.onCreate`) is deliberate:
 * the callback fires only on first DB *creation*, so it would MISS users migrated v8→v9 (whose DB
 * already existed). Running every launch and seeding only when empty covers BOTH fresh installs and
 * migrated users, and self-heals a prior failed/partial seed.
 *
 * Never throws: the repository swallows+logs parse failures and this wraps the whole call defensively,
 * so a catalog problem can never crash app start.
 */
@Singleton
class ExerciseSeeder @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
) {
    suspend fun seedIfNeeded() {
        runCatching { exerciseRepository.seedIfEmpty() }
            .onFailure { t ->
                AppLogger.e(FeatureTag.EXERCISE_CATALOG, "seedIfNeeded failed; catalog may be empty", t)
            }
    }
}
