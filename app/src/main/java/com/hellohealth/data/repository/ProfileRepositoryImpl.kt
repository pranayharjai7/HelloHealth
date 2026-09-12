package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.ProfileDao
import com.hellohealth.data.local.entities.ProfileEntity
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.sync.SyncScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first profile repository (Supabase `profiles`, conflict key `id`). Reads come from Room;
 * writes hit Room (`isSynced=false`) then poke [SyncScheduler]. [com.hellohealth.sync.ProfileSyncer]
 * owns the Supabase push/pull.
 */
@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler
) : ProfileRepository {

    override suspend fun getProfile(): UserProfile? {
        val userId = sessionManager.getCurrentUserId() ?: return null
        return profileDao.get(userId)?.let { UserProfile(displayName = it.displayName) }
    }

    override suspend fun upsertProfile(profile: UserProfile) {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.PROFILE, "upsertProfile with no signed-in user; dropping write")
            return
        }

        profileDao.upsert(
            ProfileEntity(
                userId = userId,
                displayName = profile.displayName?.trim().orEmpty().ifBlank { null },
                updatedAtEpochMs = Timestamps.nowEpochMs(),
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = null,
                isSynced = false
            )
        )
        AppLogger.d(FeatureTag.PROFILE, "profile written locally; requesting sync")
        syncScheduler.requestSync()
    }
}
