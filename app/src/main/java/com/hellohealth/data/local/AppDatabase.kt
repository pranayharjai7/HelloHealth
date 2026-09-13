package com.hellohealth.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hellohealth.data.local.dao.EmotionRecordsDao
import com.hellohealth.data.local.dao.FoodPrefsDao
import com.hellohealth.data.local.dao.GoalsDao
import com.hellohealth.data.local.dao.ProfileDao
import com.hellohealth.data.local.dao.SnapshotDao
import com.hellohealth.data.local.dao.SyncLogDao
import com.hellohealth.data.local.dao.UserDao
import com.hellohealth.data.local.dao.WorkoutSessionDao
import com.hellohealth.data.local.entities.EmotionRecordEntity
import com.hellohealth.data.local.entities.FoodPrefsEntity
import com.hellohealth.data.local.entities.GoalsEntity
import com.hellohealth.data.local.entities.ProfileEntity
import com.hellohealth.data.local.entities.SnapshotEntity
import com.hellohealth.data.local.entities.SyncLogEntity
import com.hellohealth.data.local.entities.UserEntity
import com.hellohealth.data.local.entities.WorkoutSessionEntity

@Database(
    entities = [
        UserEntity::class,
        GoalsEntity::class,
        ProfileEntity::class,
        FoodPrefsEntity::class,
        SnapshotEntity::class,
        SyncLogEntity::class,
        EmotionRecordEntity::class,
        WorkoutSessionEntity::class
    ],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun goalsDao(): GoalsDao
    abstract fun profileDao(): ProfileDao
    abstract fun foodPrefsDao(): FoodPrefsDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun emotionRecordsDao(): EmotionRecordsDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
}
