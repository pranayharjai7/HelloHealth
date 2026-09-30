package com.hellohealth.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hellohealth.data.local.dao.CachedFoodDao
import com.hellohealth.data.local.dao.EmotionRecordsDao
import com.hellohealth.data.local.dao.ExerciseDao
import com.hellohealth.data.local.dao.FoodPrefsDao
import com.hellohealth.data.local.dao.GoalsDao
import com.hellohealth.data.local.dao.NutritionEntryDao
import com.hellohealth.data.local.dao.PlannedExerciseDao
import com.hellohealth.data.local.dao.ProfileDao
import com.hellohealth.data.local.dao.SnapshotDao
import com.hellohealth.data.local.dao.SyncLogDao
import com.hellohealth.data.local.dao.UserDao
import com.hellohealth.data.local.dao.VitalsSampleDao
import com.hellohealth.data.local.dao.WorkoutDayDao
import com.hellohealth.data.local.dao.WorkoutPlanDao
import com.hellohealth.data.local.entities.CachedFoodEntity
import com.hellohealth.data.local.entities.EmotionRecordEntity
import com.hellohealth.data.local.entities.ExerciseEntity
import com.hellohealth.data.local.entities.FoodPrefsEntity
import com.hellohealth.data.local.entities.GoalsEntity
import com.hellohealth.data.local.entities.NutritionEntryEntity
import com.hellohealth.data.local.entities.PlannedExerciseEntity
import com.hellohealth.data.local.entities.ProfileEntity
import com.hellohealth.data.local.entities.SnapshotEntity
import com.hellohealth.data.local.entities.SyncLogEntity
import com.hellohealth.data.local.entities.UserEntity
import com.hellohealth.data.local.entities.VitalsSampleEntity
import com.hellohealth.data.local.entities.WorkoutDayEntity
import com.hellohealth.data.local.entities.WorkoutPlanEntity

@Database(
    entities = [
        UserEntity::class,
        GoalsEntity::class,
        ProfileEntity::class,
        FoodPrefsEntity::class,
        SnapshotEntity::class,
        SyncLogEntity::class,
        EmotionRecordEntity::class,
        WorkoutPlanEntity::class,
        WorkoutDayEntity::class,
        PlannedExerciseEntity::class,
        ExerciseEntity::class,
        VitalsSampleEntity::class,
        NutritionEntryEntity::class,
        CachedFoodEntity::class
    ],
    version = 11,
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
    abstract fun workoutPlanDao(): WorkoutPlanDao
    abstract fun workoutDayDao(): WorkoutDayDao
    abstract fun plannedExerciseDao(): PlannedExerciseDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun vitalsSampleDao(): VitalsSampleDao
    abstract fun nutritionEntryDao(): NutritionEntryDao
    abstract fun cachedFoodDao(): CachedFoodDao
}
