package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.AchievementDao
import com.hellohealth.data.local.dao.PointsLedgerDao
import com.hellohealth.data.local.dao.StreakDao
import com.hellohealth.data.local.entities.AchievementEntity
import com.hellohealth.data.local.entities.PointsLedgerEntity
import com.hellohealth.data.local.entities.StreakEntity
import com.hellohealth.domain.model.EarnedAchievement
import com.hellohealth.domain.model.Valence
import com.hellohealth.domain.model.WellnessSnapshot
import com.hellohealth.domain.model.WellnessStreak
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.domain.repository.VitalsRepository
import com.hellohealth.domain.repository.WellnessRepository
import com.hellohealth.domain.wellness.Achievement
import com.hellohealth.domain.wellness.AchievementEvaluator
import com.hellohealth.domain.wellness.Pillar
import com.hellohealth.domain.wellness.StreakCalculator
import com.hellohealth.domain.wellness.WellnessScoreUseCase
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first wellness repository. Composes the four pillar sources into a live daily score via the
 * pure [WellnessScoreUseCase], and persists streak / points / achievement rows ONLY when the observed
 * day is today (per the "persist today only" decision — past-day views compute the score read-only).
 *
 * Pillar sub-scores (each 0–100, or null when the pillar has no data that day):
 *  - Activity: fraction of the three ring goals met (steps / active-calories / active-minutes).
 *  - Nutrition: calorie adherence — closeness of consumed to the goal band (any entries → tracked).
 *  - Recovery: the readiness score as-of the day (null while establishing / insufficient).
 *  - Mood: share of the day's logs that are positive-valence.
 *
 * Idempotency: the ledger id is "$userId|pts|$day|$source" so re-persisting the same day's award
 * upserts the same row; an achievement's unlock timestamp is written once and never overwritten.
 */
@Singleton
class WellnessRepositoryImpl @Inject constructor(
    private val goalsRepository: GoalsRepository,
    private val activityRepository: ActivityRepository,
    private val nutritionRepository: NutritionRepository,
    private val vitalsRepository: VitalsRepository,
    private val emotionsRepository: EmotionsRepository,
    private val streakDao: StreakDao,
    private val pointsLedgerDao: PointsLedgerDao,
    private val achievementDao: AchievementDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler,
) : WellnessRepository {

    private val scoreUseCase = WellnessScoreUseCase()
    private val achievementEvaluator = AchievementEvaluator()

    override fun observeSnapshot(date: LocalDate): Flow<WellnessSnapshot> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptySnapshot(date))
            return@flow
        }
        val iso = date.toString()

        // Persist gamification state ONCE, up front, for TODAY only — decoupled from the reactive
        // read below. Doing the write inside the observed combine created a write→observe→write loop
        // (persisting mutates the streak/ledger tables the combine observes). Past days are read-only.
        if (date == LocalDate.now()) {
            computeAndPersistToday(userId, date)
        }

        // Pure READ path: observe the pillar signals (for a live score) + the gamification tables (for
        // streaks/points/achievements). No writes happen here, so there is nothing to re-trigger it.
        val goals = goalsRepository.getCurrentActivityGoals()
        val dayStat = activityRepository.fetchWeeklyStats().dailyStats.firstOrNull { it.date == date }
        val activityScore = dayStat?.let { s ->
            val met = listOf(
                s.steps >= goals.steps,
                s.calories >= goals.activeCalories,
                s.activeMinutes >= goals.activeMinutes,
            ).count { it }
            (met * 100) / 3
        }

        emitAll(
            combine(
                nutritionRepository.observeDaySummary(iso),
                vitalsRepository.observeReadinessAsOf(date),
                emotionsRepository.observeWindow(date.toEpochDay(), date.toEpochDay()),
                streakDao.observeForUser(userId),
                pointsLedgerDao.observeTotalForUser(userId),
            ) { nutrition, readiness, moodLogs, streaks, totalPoints ->
                val nutritionScore = if (nutrition.caloriesConsumed > 0) {
                    calorieAdherence(nutrition.caloriesConsumed, goals.activeCalories)
                } else {
                    null
                }
                val recovery = readiness?.score?.takeIf { it > 0 }
                val mood = moodLogs.takeIf { it.isNotEmpty() }?.let { logs ->
                    (logs.count { it.emotion.valence == Valence.POSITIVE } * 100) / logs.size
                }
                val wellness = scoreUseCase(
                    activity = activityScore,
                    nutrition = nutritionScore,
                    recovery = recovery,
                    mood = mood,
                )
                buildSnapshot(iso, wellness, streaks, totalPoints, achievementDao.snapshotForUser(userId))
            }
        )
    }.flowOn(Dispatchers.IO)

    // ---------------------------------------------------------------- Persistence (today only)

    /** Compute today's pillars and persist streak/ledger/achievement rows (change-detected). */
    private suspend fun computeAndPersistToday(userId: String, date: LocalDate) {
        val iso = date.toString()
        val goals = goalsRepository.getCurrentActivityGoals()
        val dayStat = activityRepository.fetchWeeklyStats().dailyStats.firstOrNull { it.date == date }
        val nutrition = nutritionRepository.observeDaySummary(iso).first()
        val readiness = vitalsRepository.observeReadinessAsOf(date).first()
        val moodLogs = emotionsRepository.observeWindow(date.toEpochDay(), date.toEpochDay()).first()

        val activity = dayStat?.let { s ->
            val met = listOf(
                s.steps >= goals.steps,
                s.calories >= goals.activeCalories,
                s.activeMinutes >= goals.activeMinutes,
            ).count { it }
            (met * 100) / 3
        }
        val nutritionScore = if (nutrition.caloriesConsumed > 0) calorieAdherence(nutrition.caloriesConsumed, goals.activeCalories) else null
        val recovery = readiness?.score?.takeIf { it > 0 }
        val mood = moodLogs.takeIf { it.isNotEmpty() }?.let { logs ->
            (logs.count { it.emotion.valence == Valence.POSITIVE } * 100) / logs.size
        }
        val score = scoreUseCase(activity = activity, nutrition = nutritionScore, recovery = recovery, mood = mood).score
        persistToday(userId, iso, score, activity, nutritionScore, recovery, mood)
    }

    private suspend fun persistToday(
        userId: String,
        iso: String,
        score: Int?,
        activity: Int?,
        nutrition: Int?,
        recovery: Int?,
        mood: Int?,
    ) {
        val now = Timestamps.nowEpochMs()
        val tz = Timestamps.currentTzOffsetMinutes()
        val hitPillars = buildSet {
            if ((activity ?: 0) >= PILLAR_HIT_BAR) add(Pillar.ACTIVITY)
            if ((nutrition ?: 0) >= PILLAR_HIT_BAR) add(Pillar.NUTRITION)
            if ((recovery ?: 0) >= PILLAR_HIT_BAR) add(Pillar.RECOVERY)
            if ((mood ?: 0) >= PILLAR_HIT_BAR) add(Pillar.MOOD)
        }
        val trackedPillars = buildSet {
            if (activity != null) add(Pillar.ACTIVITY)
            if (nutrition != null) add(Pillar.NUTRITION)
            if (recovery != null) add(Pillar.RECOVERY)
            if (mood != null) add(Pillar.MOOD)
        }

        var wroteAny = false

        // Per-pillar streaks: bump currentCount if hit today, reset to 0 if tracked-but-missed, leave
        // alone if untracked (yesterday's run neither grows nor breaks without a signal today).
        // CRITICAL: only upsert when the row's VALUE actually changes. An unconditional re-write (with
        // a fresh updatedAt) would re-emit the observed streak flow and loop recompute→persist forever.
        for (pillar in trackedPillars) {
            val hit = pillar in hitPillars
            val id = "$userId|streak|${pillar.name.lowercase()}"
            val existing = streakDao.getById(id)
            val yesterdayHit = existing?.lastHitLocalDate == LocalDate.parse(iso).minusDays(1).toString()
            val newCurrent = when {
                !hit -> 0
                existing == null -> 1
                existing.lastHitLocalDate == iso -> existing.currentCount // already counted today
                yesterdayHit -> existing.currentCount + 1
                else -> 1
            }
            val newLongest = maxOf(existing?.longestCount ?: 0, newCurrent)
            val newLastHit = if (hit) iso else existing?.lastHitLocalDate
            // No-op if the stored row already matches — this is what stops the loop.
            if (existing != null &&
                existing.currentCount == newCurrent &&
                existing.longestCount == newLongest &&
                existing.lastHitLocalDate == newLastHit
            ) {
                continue
            }
            streakDao.upsert(
                StreakEntity(
                    id = id,
                    userId = userId,
                    pillar = pillar.name.lowercase(),
                    currentCount = newCurrent,
                    longestCount = newLongest,
                    lastHitLocalDate = newLastHit,
                    updatedAtEpochMs = now,
                    updatedAtTzOffsetMinutes = tz,
                    deletedAtEpochMs = null,
                    isSynced = false,
                )
            )
            wroteAny = true
        }

        // Points: award today's score as a single idempotent (day, "wellness") ledger row — but only
        // re-write when the points value actually changes (same loop-prevention reason).
        if (score != null) {
            val id = "$userId|pts|$iso|wellness"
            val existing = pointsLedgerDao.getById(id)
            if (existing?.points != score) {
                pointsLedgerDao.upsert(
                    PointsLedgerEntity(
                        id = id,
                        userId = userId,
                        localDate = iso,
                        source = "wellness",
                        points = score,
                        updatedAtEpochMs = now,
                        updatedAtTzOffsetMinutes = tz,
                        deletedAtEpochMs = null,
                        isSynced = false,
                    )
                )
                wroteAny = true
            }
        }

        // Achievements: evaluate against progress; unlock any newly-earned one ONCE.
        val progress = buildProgress(userId, score)
        for (achievement in achievementEvaluator.earned(progress)) {
            val id = "$userId|ach|${achievement.code}"
            if (achievementDao.getById(id) != null) continue // already unlocked — never overwrite
            achievementDao.upsert(
                AchievementEntity(
                    id = id,
                    userId = userId,
                    code = achievement.code,
                    unlockedAtEpochMs = now,
                    updatedAtEpochMs = now,
                    updatedAtTzOffsetMinutes = tz,
                    deletedAtEpochMs = null,
                    isSynced = false,
                )
            )
            wroteAny = true
        }

        if (wroteAny) {
            AppLogger.d(FeatureTag.WELLNESS, "persisted today's wellness state; requesting sync")
            syncScheduler.requestSync()
        }
    }

    private suspend fun buildProgress(userId: String, todayScore: Int?): AchievementEvaluator.Progress {
        // Longest streaks come from the stored rows; totals come from the ledger. These are one-shot
        // reads (not the observed flow) so the evaluation reflects the just-written state.
        val allStreaks = streakDao.snapshotForUser(userId)
        val longestBalanced = allStreaks.firstOrNull { it.pillar == "balanced" }?.longestCount ?: 0
        val longestActivity = allStreaks.firstOrNull { it.pillar == Pillar.ACTIVITY.name.lowercase() }?.longestCount ?: 0
        val workouts = pointsLedgerDao.countForSource(userId, "workout")
        val bestScore = maxOf(todayScore ?: 0, pointsLedgerDao.maxPointsForSource(userId, "wellness"))
        return AchievementEvaluator.Progress(
            longestBalancedStreak = longestBalanced,
            longestActivityStreak = longestActivity,
            totalWorkoutsLogged = workouts,
            bestWellnessScore = bestScore,
        )
    }

    // ---------------------------------------------------------------- Snapshot assembly

    private suspend fun buildSnapshot(
        iso: String,
        wellness: com.hellohealth.domain.wellness.WellnessScore,
        streaks: List<StreakEntity>,
        totalPoints: Int,
        earned: List<AchievementEntity>,
    ): WellnessSnapshot {
        val streakViews = streaks
            .sortedByDescending { it.currentCount }
            .map { s ->
                WellnessStreak(
                    pillarKey = s.pillar,
                    label = streakLabel(s.pillar),
                    currentCount = s.currentCount,
                    longestCount = s.longestCount,
                )
            }
        val earnedViews = earned.mapNotNull { row ->
            Achievement.entries.firstOrNull { it.code == row.code }?.let {
                EarnedAchievement(achievement = it, unlockedAtEpochMs = row.unlockedAtEpochMs)
            }
        }
        return WellnessSnapshot(
            localDate = iso,
            score = wellness.score,
            band = wellness.band,
            pillars = wellness.pillars,
            streaks = streakViews,
            totalPoints = totalPoints,
            earnedAchievements = earnedViews,
        )
    }

    private fun emptySnapshot(date: LocalDate) = WellnessSnapshot(
        localDate = date.toString(),
        score = null,
        band = null,
        pillars = emptyList(),
        streaks = emptyList(),
        totalPoints = 0,
        earnedAchievements = emptyList(),
    )

    private fun streakLabel(pillarKey: String): String = when (pillarKey) {
        "balanced" -> "Balanced days"
        else -> Pillar.entries.firstOrNull { it.name.lowercase() == pillarKey }?.displayName ?: pillarKey
    }

    /** Calorie adherence 0–100: 100 at/under the active-calorie goal, tapering as consumption exceeds it. */
    private fun calorieAdherence(consumed: Double, goal: Int): Int {
        if (goal <= 0) return 50
        val ratio = consumed / goal
        return when {
            ratio <= 1.0 -> 100
            ratio >= 2.0 -> 0
            else -> ((2.0 - ratio) * 100).toInt()
        }.coerceIn(0, 100)
    }

    private companion object {
        /** A pillar "counts as hit" for streak purposes at or above this sub-score. */
        const val PILLAR_HIT_BAR = 60
    }
}
