package com.hellohealth.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class LwwResolverTest {

    @Test
    fun `remote strictly newer wins`() {
        assertEquals(LwwResolver.Winner.REMOTE, LwwResolver.resolve(localUpdatedAtEpochMs = 100L, remoteUpdatedAtEpochMs = 200L))
    }

    @Test
    fun `local newer wins`() {
        assertEquals(LwwResolver.Winner.LOCAL, LwwResolver.resolve(localUpdatedAtEpochMs = 300L, remoteUpdatedAtEpochMs = 200L))
    }

    @Test
    fun `exact tie favors local`() {
        // Push runs before pull, so local was already pushed; favoring local on a tie is safe.
        assertEquals(LwwResolver.Winner.LOCAL, LwwResolver.resolve(localUpdatedAtEpochMs = 200L, remoteUpdatedAtEpochMs = 200L))
    }

    @Test
    fun `null remote (server has no updated_at yet) never wins`() {
        assertEquals(LwwResolver.Winner.LOCAL, LwwResolver.resolve(localUpdatedAtEpochMs = 1L, remoteUpdatedAtEpochMs = null))
    }

    @Test
    fun `null local means remote wins when it has a clock`() {
        assertEquals(LwwResolver.Winner.REMOTE, LwwResolver.resolve(localUpdatedAtEpochMs = null, remoteUpdatedAtEpochMs = 1L))
    }

    @Test
    fun `both null favors local (nothing to pull)`() {
        assertEquals(LwwResolver.Winner.LOCAL, LwwResolver.resolve(localUpdatedAtEpochMs = null, remoteUpdatedAtEpochMs = null))
    }
}
