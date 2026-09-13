package com.hellohealth.di

import com.hellohealth.data.repository.AuthRepositoryImpl
import com.hellohealth.data.repository.EmotionDetectionRepositoryImpl
import com.hellohealth.data.repository.EmotionsRepositoryImpl
import com.hellohealth.data.repository.GoalsRepositoryImpl
import com.hellohealth.data.repository.ProfileRepositoryImpl
import com.hellohealth.data.repository.UserRepositoryImpl
import com.hellohealth.data.repository.WorkoutRepositoryImpl
import com.hellohealth.domain.repository.AuthRepository
import com.hellohealth.domain.repository.EmotionDetectionRepository
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.domain.repository.UserRepository
import com.hellohealth.domain.repository.WorkoutRepository
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
    abstract fun bindWorkoutRepository(
        workoutRepositoryImpl: WorkoutRepositoryImpl
    ): WorkoutRepository
}
