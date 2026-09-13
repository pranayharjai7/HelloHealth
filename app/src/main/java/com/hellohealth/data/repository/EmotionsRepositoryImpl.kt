package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.EmotionRecordsDao
import com.hellohealth.data.local.entities.EmotionRecordEntity
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first mood repository. Writes hit Room with `isSynced=false` then poke [SyncScheduler];
 * the [com.hellohealth.sync.EmotionsSyncer] owns the Supabase round-trip. Reads are Room Flows.
 *
 * With no signed-in user, reads emit empty/null and writes are dropped with a warning — matching
 * the GoalsRepositoryImpl contract so screens never special-case a missing session.
 */
@Singleton
class EmotionsRepositoryImpl @Inject constructor(
    private val emotionRecordsDao: EmotionRecordsDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler
) : EmotionsRepository {

    override fun observeToday(): Flow<List<EmotionRecord>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        emitAll(emotionRecordsDao.observeForDay(userId, today).map { rows -> rows.map { it.toDomain() } })
    }.flowOn(Dispatchers.IO)

    override fun observeLatest(): Flow<EmotionRecord?> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(null)
            return@flow
        }
        emitAll(emotionRecordsDao.observeLatest(userId).map { it?.toDomain() })
    }.flowOn(Dispatchers.IO)

    override fun observeWindow(startEpochDay: Long, endEpochDay: Long): Flow<List<EmotionRecord>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        val start = LocalDate.ofEpochDay(startEpochDay).toString()
        val end = LocalDate.ofEpochDay(endEpochDay).toString()
        emitAll(emotionRecordsDao.observeWindow(userId, start, end).map { rows -> rows.map { it.toDomain() } })
    }.flowOn(Dispatchers.IO)

    override suspend fun logEmotion(
        emotion: EmotionType,
        confidence: Double,
        source: String,
        note: String?,
        visibility: String
    ): String? {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.EMOTIONS, "logEmotion with no signed-in user; dropping write")
            return null
        }

        val nowMs = Timestamps.nowEpochMs()
        val zone = ZoneId.systemDefault()
        val localDate = Timestamps.epochMsToInstant(nowMs).atZone(zone).toLocalDate().toString()

        // Deterministic id: keyed on user+instant+emotion so re-logging the identical mood at
        // the same millisecond upserts instead of duplicating, while a different mood in the
        // same instant is a distinct row. Avoids Math.random()/UUID (non-deterministic).
        val id = "$userId|$nowMs|${emotion.name}"

        emotionRecordsDao.upsert(
            EmotionRecordEntity(
                id = id,
                userId = userId,
                timestampUtcEpochMs = nowMs,
                tzOffsetMinutes = Timestamps.currentTzOffsetMinutes(zone),
                localDate = localDate,
                emotion = emotion.name,
                confidence = confidence,
                source = source,
                note = note,
                visibility = visibility,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(zone),
                deletedAtEpochMs = null,
                isSynced = false
            )
        )
        AppLogger.d(FeatureTag.EMOTIONS, "mood '${emotion.name}' logged locally; requesting sync")
        syncScheduler.requestSync()
        return id
    }

    override suspend fun delete(id: String) {
        val existing = emotionRecordsDao.getById(id)
        if (existing == null) {
            AppLogger.w(FeatureTag.EMOTIONS, "delete for unknown mood id=$id; ignoring")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        emotionRecordsDao.upsert(
            existing.copy(
                deletedAtEpochMs = nowMs,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                isSynced = false
            )
        )
        AppLogger.d(FeatureTag.EMOTIONS, "mood id=$id tombstoned; requesting sync")
        syncScheduler.requestSync()
    }

    private fun EmotionRecordEntity.toDomain() = EmotionRecord(
        id = id,
        userId = userId,
        timestampUtcEpochMs = timestampUtcEpochMs,
        tzOffsetMinutes = tzOffsetMinutes,
        emotion = EmotionType.fromName(emotion),
        confidence = confidence,
        source = source,
        note = note,
        visibility = visibility
    )
}
