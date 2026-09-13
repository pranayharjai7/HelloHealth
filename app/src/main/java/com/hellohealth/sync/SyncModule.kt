package com.hellohealth.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Binds each concrete [Syncer] into the multibound `Set<Syncer>` the [SyncOrchestrator] injects.
 * Adding a new table's sync support is one `@Binds @IntoSet` line here — the orchestrator picks it
 * up automatically with no other wiring.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @IntoSet
    abstract fun bindGoalsSyncer(impl: GoalsSyncer): Syncer

    @Binds
    @IntoSet
    abstract fun bindProfileSyncer(impl: ProfileSyncer): Syncer

    @Binds
    @IntoSet
    abstract fun bindFoodPrefsSyncer(impl: FoodPrefsSyncer): Syncer

    @Binds
    @IntoSet
    abstract fun bindSnapshotSyncer(impl: SnapshotSyncer): Syncer

    @Binds
    @IntoSet
    abstract fun bindEmotionsSyncer(impl: EmotionsSyncer): Syncer

    @Binds
    @IntoSet
    abstract fun bindWorkoutSyncer(impl: WorkoutSessionSyncer): Syncer
}
