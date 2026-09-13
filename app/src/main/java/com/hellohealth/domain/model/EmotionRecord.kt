package com.hellohealth.domain.model

/**
 * A single logged mood. Multiple records per user per day are expected (the store is day-keyed
 * only for "today's dominant" rollups, not deduped).
 *
 * [id] is a stable, deterministic identifier (see EmotionsRepositoryImpl) so a re-log of the same
 * instant upserts rather than duplicates, and so sync conflict resolution has a fixed key.
 * [timestampUtcEpochMs] is the moment the mood was logged (UTC epoch millis); [tzOffsetMinutes]
 * is the device offset at that moment for audit/display only — never used for ordering or LWW.
 *
 * [source] is "manual" in P1; the P2 face-scan path will write "camera". [confidence] is 1.0 for
 * manual logs and the model's softmax score for camera logs.
 */
data class EmotionRecord(
    val id: String,
    val userId: String,
    val timestampUtcEpochMs: Long,
    val tzOffsetMinutes: Int,
    val emotion: EmotionType,
    val confidence: Double = 1.0,
    val source: String = SOURCE_MANUAL,
    val note: String? = null,
    val visibility: String = VISIBILITY_PRIVATE
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_CAMERA = "camera"
        const val VISIBILITY_PRIVATE = "private"
    }
}
