package com.hellohealth.data.local

import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.SnapshotEntity
import com.hellohealth.domain.model.DailyHealthSnapshot
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.model.SnapshotDataSource
import com.hellohealth.domain.model.SnapshotSyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Maps the Room [SnapshotEntity] to/from the domain [DailyHealthSnapshot]. The [HealthSummary]
 * payload rides in the entity's `summaryJson` blob (see [SnapshotJson]).
 */
object SnapshotMapper {

    fun toEntity(
        userId: String,
        date: LocalDate,
        summary: HealthSummary,
        syncStatus: SnapshotSyncStatus,
        dataSource: SnapshotDataSource,
        lastSyncedAtEpochMs: Long,
        updatedAtEpochMs: Long,
        isSynced: Boolean,
        snapshotTimezone: String = ZoneId.systemDefault().id
    ) = SnapshotEntity(
        userId = userId,
        snapshotDate = date.toString(),
        snapshotTimezone = snapshotTimezone,
        syncStatus = syncStatus.name.lowercase(),
        dataSource = dataSource.name.lowercase(),
        lastSyncedAtEpochMs = lastSyncedAtEpochMs,
        summaryJson = SnapshotJson.encode(summary),
        updatedAtEpochMs = updatedAtEpochMs,
        updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
        deletedAtEpochMs = null,
        isSynced = isSynced
    )

    fun toDomain(entity: SnapshotEntity) = DailyHealthSnapshot(
        date = LocalDate.parse(entity.snapshotDate),
        summary = SnapshotJson.decode(entity.summaryJson),
        snapshotTimezone = entity.snapshotTimezone,
        lastSyncedAt = Instant.ofEpochMilli(entity.lastSyncedAtEpochMs),
        syncStatus = runCatching { SnapshotSyncStatus.valueOf(entity.syncStatus.uppercase()) }
            .getOrDefault(SnapshotSyncStatus.COMPLETE),
        dataSource = runCatching { SnapshotDataSource.valueOf(entity.dataSource.uppercase()) }
            .getOrDefault(SnapshotDataSource.HEALTH_CONNECT)
    )
}
