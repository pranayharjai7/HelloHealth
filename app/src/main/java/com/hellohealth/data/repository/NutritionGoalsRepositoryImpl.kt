package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.NutritionGoalsDao
import com.hellohealth.data.local.entities.NutritionGoalsEntity
import com.hellohealth.domain.health.MicronutrientTargets.Micro
import com.hellohealth.domain.model.nutrition.MacroMode
import com.hellohealth.domain.model.nutrition.NutritionGoals
import com.hellohealth.domain.repository.NutritionGoalsRepository
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first nutrition-goals repository. Room is the source of truth the UI reads; writes hit Room
 * (`isSynced=false`) then poke [SyncScheduler]. Mirrors [GoalsRepositoryImpl]. No signed-in user or no
 * stored row → [NutritionGoals.DEFAULT] (auto everything), so the screen degrades to the derivation.
 *
 * The domain↔entity mapping lives here: [NutritionGoals.trackedMicros] ⇄ a comma-joined name list, and
 * [NutritionGoals.macroMode] ⇄ the enum name — both parsed defensively so a corrupt/legacy value falls
 * back to the default rather than throwing.
 */
@Singleton
class NutritionGoalsRepositoryImpl @Inject constructor(
    private val dao: NutritionGoalsDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler,
) : NutritionGoalsRepository {

    override fun observeNutritionGoals(): Flow<NutritionGoals> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(NutritionGoals.DEFAULT)
            return@flow
        }
        emitAll(dao.observe(userId).map { it?.toDomain() ?: NutritionGoals.DEFAULT })
    }.flowOn(Dispatchers.IO)

    override suspend fun getNutritionGoals(): NutritionGoals {
        val userId = sessionManager.getCurrentUserId() ?: return NutritionGoals.DEFAULT
        return dao.get(userId)?.toDomain() ?: NutritionGoals.DEFAULT
    }

    override suspend fun updateNutritionGoals(goals: NutritionGoals) {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.NUTRITION, "updateNutritionGoals with no signed-in user; dropping write")
            return
        }
        dao.upsert(
            NutritionGoalsEntity(
                userId = userId,
                useCustomCalories = goals.useCustomCalories,
                calorieBudget = goals.calorieBudget,
                useCustomMacros = goals.useCustomMacros,
                macroMode = goals.macroMode.name,
                proteinG = goals.proteinG,
                carbsG = goals.carbsG,
                fatG = goals.fatG,
                fiberG = goals.fiberG,
                trackedMicros = goals.trackedMicros.joinToString(",") { it.name },
                updatedAtEpochMs = Timestamps.nowEpochMs(),
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = null,
                isSynced = false,
            )
        )
        AppLogger.d(FeatureTag.NUTRITION, "nutrition goals written locally; requesting sync")
        syncScheduler.requestSync()
    }

    private fun NutritionGoalsEntity.toDomain() = NutritionGoals(
        useCustomCalories = useCustomCalories,
        calorieBudget = calorieBudget,
        useCustomMacros = useCustomMacros,
        macroMode = runCatching { MacroMode.valueOf(macroMode) }.getOrDefault(MacroMode.GRAMS),
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        fiberG = fiberG,
        trackedMicros = parseMicros(trackedMicros),
    )

    companion object {
        /** Parse the CSV micro list; unknown/blank names are dropped, empty → all (so a blank never hides everything). */
        internal fun parseMicros(csv: String): Set<Micro> {
            val parsed = csv.split(",")
                .mapNotNull { name -> runCatching { Micro.valueOf(name.trim()) }.getOrNull() }
                .toSet()
            return parsed.ifEmpty { Micro.entries.toSet() }
        }
    }
}
