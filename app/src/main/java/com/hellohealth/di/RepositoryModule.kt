package com.hellohealth.di

import com.hellohealth.data.repository.AuthRepositoryImpl
import com.hellohealth.data.repository.BodyMetricsRepositoryImpl
import com.hellohealth.data.repository.CoachingRepositoryImpl
import com.hellohealth.data.repository.EmotionDetectionRepositoryImpl
import com.hellohealth.data.repository.EmotionsRepositoryImpl
import com.hellohealth.data.repository.ExerciseRepositoryImpl
import com.hellohealth.data.repository.GoalsRepositoryImpl
import com.hellohealth.data.repository.NutritionRepositoryImpl
import com.hellohealth.data.repository.ProfileRepositoryImpl
import com.hellohealth.data.repository.UserRepositoryImpl
import com.hellohealth.data.repository.VitalsRepositoryImpl
import com.hellohealth.data.repository.WorkoutPlanRepositoryImpl
import com.hellohealth.domain.repository.AuthRepository
import com.hellohealth.domain.repository.BodyMetricsRepository
import com.hellohealth.domain.repository.CoachingRepository
import com.hellohealth.domain.repository.EmotionDetectionRepository
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.repository.ExerciseRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.domain.repository.UserRepository
import com.hellohealth.domain.repository.VitalsRepository
import com.hellohealth.domain.repository.WorkoutPlanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindGoalsRepository(
        goalsRepositoryImpl: GoalsRepositoryImpl
    ): GoalsRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        profileRepositoryImpl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindEmotionsRepository(
        emotionsRepositoryImpl: EmotionsRepositoryImpl
    ): EmotionsRepository

    @Binds
    @Singleton
    abstract fun bindEmotionDetectionRepository(
        emotionDetectionRepositoryImpl: EmotionDetectionRepositoryImpl
    ): EmotionDetectionRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(
        exerciseRepositoryImpl: ExerciseRepositoryImpl
    ): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutPlanRepository(
        workoutPlanRepositoryImpl: WorkoutPlanRepositoryImpl
    ): WorkoutPlanRepository

    @Binds
    @Singleton
    abstract fun bindVitalsRepository(
        vitalsRepositoryImpl: VitalsRepositoryImpl
    ): VitalsRepository

    @Binds
    @Singleton
    abstract fun bindNutritionRepository(
        nutritionRepositoryImpl: NutritionRepositoryImpl
    ): NutritionRepository

    @Binds
    @Singleton
    abstract fun bindCoachingRepository(
        coachingRepositoryImpl: CoachingRepositoryImpl
    ): CoachingRepository

    @Binds
    @Singleton
    abstract fun bindBodyMetricsRepository(
        bodyMetricsRepositoryImpl: BodyMetricsRepositoryImpl
    ): BodyMetricsRepository
}
