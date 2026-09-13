package com.hellohealth.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the process-lifetime [CoroutineScope] — one that lives for the whole application and is never
 * cancelled by a screen/ViewModel teardown. Use it for must-finish local work that is triggered by
 * leaving a screen (e.g. flushing a staged mood delete on navigate-away): launching such work on the
 * screen's own `viewModelScope` would let the NavBackStackEntry teardown cancel the coroutine before
 * the Room write lands, silently losing it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    /**
     * A [SupervisorJob]-backed scope so one failed child never tears down the others, on the default
     * dispatcher (the enclosed work is Room I/O that itself hops to Room's executor). Deliberately
     * un-cancelled for the process lifetime — the app has no user-driven shutdown, and the OS reclaims
     * it with the process.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
