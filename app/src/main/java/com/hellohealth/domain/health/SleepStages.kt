package com.hellohealth.domain.health

/**
 * Pure sleep-stage derivation from Health Connect `SleepSessionRecord.stages`. No I/O, no Android — the
 * caller flattens the day's stage segments into [SleepStageSegment]s and this folds them into a
 * [SleepStageSummary] (per-stage minutes + latency + efficiency + WASO). Fully unit-tested.
 *
 * Stage ints mirror androidx.health.connect SleepSessionRecord.Stage.STAGE_TYPE_*:
 *   0 UNKNOWN · 1 AWAKE · 2 SLEEPING · 3 OUT_OF_BED · 4 LIGHT · 5 DEEP · 6 REM · 7 AWAKE_IN_BED.
 */
object SleepStages {
    const val UNKNOWN = 0
    const val AWAKE = 1
    const val SLEEPING = 2
    const val OUT_OF_BED = 3
    const val LIGHT = 4
    const val DEEP = 5
    const val REM = 6
    const val AWAKE_IN_BED = 7

    private val ASLEEP = setOf(SLEEPING, LIGHT, DEEP, REM)
    private val AWAKE_SET = setOf(AWAKE, AWAKE_IN_BED)

    /**
     * @param sessionStartMs/[sessionEndMs] the sleep session span (in-bed window).
     * @param segments the session's stage segments (any order; this sorts them).
     */
    fun summarize(sessionStartMs: Long, sessionEndMs: Long, segments: List<SleepStageSegment>): SleepStageSummary {
        val inBedMin = ((sessionEndMs - sessionStartMs) / 60_000L).coerceAtLeast(0L)
        if (segments.isEmpty()) {
            // Duration-only session (no stages): everything in-bed counts as asleep, flagged estimate.
            return SleepStageSummary(
                deepMinutes = null, remMinutes = null, lightMinutes = null, awakeMinutes = null,
                asleepMinutes = inBedMin, inBedMinutes = inBedMin,
                latencyMinutes = null, efficiencyPercent = null, wasoMinutes = null,
                hasStages = false,
            )
        }
        val sorted = segments.sortedBy { it.startMs }
        fun minutesOf(predicate: (Int) -> Boolean) =
            sorted.filter { predicate(it.stage) }.sumOf { (it.endMs - it.startMs) / 60_000L }.coerceAtLeast(0L)

        val deep = minutesOf { it == DEEP }
        val rem = minutesOf { it == REM }
        val light = minutesOf { it == LIGHT || it == SLEEPING }
        val awake = minutesOf { it in AWAKE_SET }
        val asleep = minutesOf { it in ASLEEP }

        val firstAsleep = sorted.firstOrNull { it.stage in ASLEEP }
        val lastAsleep = sorted.lastOrNull { it.stage in ASLEEP }
        val latency = firstAsleep?.let { ((it.startMs - sessionStartMs) / 60_000L).coerceAtLeast(0L) }
        // WASO = awake time strictly BETWEEN first and last asleep stage.
        val waso = if (firstAsleep != null && lastAsleep != null) {
            sorted.filter { it.stage in AWAKE_SET && it.startMs >= firstAsleep.startMs && it.endMs <= lastAsleep.endMs }
                .sumOf { (it.endMs - it.startMs) / 60_000L }.coerceAtLeast(0L)
        } else null
        val efficiency = if (inBedMin > 0) ((asleep.toDouble() / inBedMin) * 100.0).coerceIn(0.0, 100.0) else null

        return SleepStageSummary(
            deepMinutes = deep, remMinutes = rem, lightMinutes = light, awakeMinutes = awake,
            asleepMinutes = asleep, inBedMinutes = inBedMin,
            latencyMinutes = latency, efficiencyPercent = efficiency, wasoMinutes = waso,
            hasStages = true,
        )
    }
}

/** One stage band: [stage] held from [startMs] to [endMs] (UTC epoch ms). */
data class SleepStageSegment(val startMs: Long, val endMs: Long, val stage: Int)

/**
 * Derived per-night sleep architecture. Nullable per-stage fields when stages are absent
 * ([hasStages] = false → a duration-only night). All minutes are whole minutes.
 */
data class SleepStageSummary(
    val deepMinutes: Long?,
    val remMinutes: Long?,
    val lightMinutes: Long?,
    val awakeMinutes: Long?,
    val asleepMinutes: Long,
    val inBedMinutes: Long,
    val latencyMinutes: Long?,
    val efficiencyPercent: Double?,
    val wasoMinutes: Long?,
    val hasStages: Boolean,
)
