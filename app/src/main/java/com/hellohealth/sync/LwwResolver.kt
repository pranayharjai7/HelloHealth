package com.hellohealth.sync

/**
 * Last-Write-Wins conflict resolution over the [com.hellohealth.data.local.Syncable] clock
 * (`updatedAtEpochMs`, UTC millis). Pure and side-effect-free so it is exhaustively unit-testable.
 *
 * Tombstones (deletes) participate in LWW like any other write: a delete at T beats an edit at
 * T-1, and an edit at T+1 resurrects a delete at T. The winner is decided purely by timestamp;
 * whether the winner is a tombstone is a separate property the caller inspects.
 */
object LwwResolver {

    enum class Winner { LOCAL, REMOTE }

    /**
     * Decide which side wins given each side's LWW clock. Ties go to [Winner.LOCAL] — during
     * push-before-pull a local row is only compared against remote after it has been pushed, so
     * favoring local on an exact-millis tie can never drop a newer remote edit.
     *
     * @param localUpdatedAtEpochMs local row's clock, or null if there is no local row
     * @param remoteUpdatedAtEpochMs remote row's clock (use 0 when the server has no `updated_at`
     *        yet, so remote never wins and sync degrades to push-only losslessly)
     */
    fun resolve(localUpdatedAtEpochMs: Long?, remoteUpdatedAtEpochMs: Long?): Winner {
        val local = localUpdatedAtEpochMs ?: Long.MIN_VALUE
        val remote = remoteUpdatedAtEpochMs ?: Long.MIN_VALUE
        return if (remote > local) Winner.REMOTE else Winner.LOCAL
    }
}
