package com.hellohealth.di

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.hellohealth.data.repository.HealthConnectRepository
import com.hellohealth.domain.repository.ActivityRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthModule {

    @Binds
    @Singleton
    abstract fun bindActivityRepository(
        healthConnectRepository: HealthConnectRepository
    ): ActivityRepository

    companion object {
        @Provides
        @Singleton
        fun provideHealthConnectClient(@ApplicationContext context: Context): HealthConnectClient? {
            return try {
                HealthConnectClient.getOrCreate(context)
            } catch (e: Exception) {
                null
            }
        }
    }
}
