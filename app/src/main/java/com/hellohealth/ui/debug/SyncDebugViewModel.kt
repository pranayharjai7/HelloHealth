package com.hellohealth.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.data.local.dao.FoodPrefsDao
import com.hellohealth.data.local.dao.GoalsDao
import com.hellohealth.data.local.dao.ProfileDao
import com.hellohealth.data.local.dao.SnapshotDao
import com.hellohealth.data.local.dao.SyncLogDao
import com.hellohealth.data.local.entities.SyncLogEntity
import com.hellohealth.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the hidden sync-debug screen. Reads the [SyncLogDao] audit trail plus the per-feature
 * unsynced (`isSynced = false`) row counts, and lets the developer force a sync run. Never linked
 * from normal UI — reached only via the 7-tap gesture on the Profile title.
 */
@HiltViewModel
class SyncDebugViewModel @Inject constructor(
    syncLogDao: SyncLogDao,
    private val goalsDao: GoalsDao,
    private val profileDao: ProfileDao,
    private val foodPrefsDao: FoodPrefsDao,
    private val snapshotDao: SnapshotDao,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    val logs: StateFlow<List<SyncLogEntity>> = syncLogDao.observeRecent(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _unsynced = MutableStateFlow(UnsyncedCounts())
    val unsynced: StateFlow<UnsyncedCounts> = _unsynced.asStateFlow()

    init {
        refreshUnsynced()
    }

    fun refreshUnsynced() {
        viewModelScope.launch {
            _unsynced.value = UnsyncedCounts(
                goals = runCatching { goalsDao.getUnsynced().size }.getOrDefault(0),
                profile = runCatching { profileDao.getUnsynced().size }.getOrDefault(0),
                foodPrefs = runCatching { foodPrefsDao.getUnsynced().size }.getOrDefault(0),
                snapshots = runCatching { snapshotDao.getUnsynced().size }.getOrDefault(0)
            )
        }
    }

    fun forceSync() {
        syncScheduler.requestSync()
    }

    data class UnsyncedCounts(
        val goals: Int = 0,
        val profile: Int = 0,
        val foodPrefs: Int = 0,
        val snapshots: Int = 0
    ) {
        val total: Int get() = goals + profile + foodPrefs + snapshots
    }
}
