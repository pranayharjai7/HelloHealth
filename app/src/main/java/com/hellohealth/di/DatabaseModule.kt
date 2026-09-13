package com.hellohealth.di

import android.content.Context
import androidx.room.Room
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.MIGRATION_3_4
import com.hellohealth.data.local.MIGRATION_4_5
import com.hellohealth.data.local.MIGRATION_5_6
import com.hellohealth.data.local.MIGRATION_6_7
import com.hellohealth.data.local.MIGRATION_7_8
import com.hellohealth.data.local.dao.EmotionRecordsDao
import com.hellohealth.data.local.dao.FoodPrefsDao
import com.hellohealth.data.local.dao.GoalsDao
import com.hellohealth.data.local.dao.ProfileDao
import com.hellohealth.data.local.dao.SnapshotDao
import com.hellohealth.data.local.dao.SyncLogDao
import com.hellohealth.data.local.dao.UserDao
import com.hellohealth.data.local.dao.WorkoutSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hello_health.db"
        )
            // Real migrations — no destructive fallback. Never silently wipe user data.
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()
    }

    @Provides
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    @Provides
    fun provideGoalsDao(database: AppDatabase): GoalsDao = database.goalsDao()

    @Provides
    fun provideProfileDao(database: AppDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideFoodPrefsDao(database: AppDatabase): FoodPrefsDao = database.foodPrefsDao()

    @Provides
    fun provideSnapshotDao(database: AppDatabase): SnapshotDao = database.snapshotDao()

    @Provides
    fun provideSyncLogDao(database: AppDatabase): SyncLogDao = database.syncLogDao()

    @Provides
    fun provideEmotionRecordsDao(database: AppDatabase): EmotionRecordsDao = database.emotionRecordsDao()

    @Provides
    fun provideWorkoutSessionDao(database: AppDatabase): WorkoutSessionDao = database.workoutSessionDao()
}
