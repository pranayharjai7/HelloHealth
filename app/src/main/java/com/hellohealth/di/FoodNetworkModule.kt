package com.hellohealth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton

/**
 * The dedicated Ktor client for the remote food APIs (USDA FoodData Central + Open Food Facts),
 * kept entirely separate from the Supabase client's transport so nutrition search can never
 * interfere with auth/sync. Qualified [@Named][Named]`("food")` so Hilt can distinguish it.
 *
 * The JSON reader is lenient — `ignoreUnknownKeys` (these APIs return large payloads we map only a
 * slice of) and `isLenient` — and a short [HttpTimeout] keeps a slow/hung endpoint from blocking a
 * typeahead. The data sources still wrap every call in `runCatching`, so a timeout or malformed body
 * degrades to empty results / null rather than surfacing as an exception.
 */
@Module
@InstallIn(SingletonComponent::class)
object FoodNetworkModule {

    @Provides
    @Singleton
    @Named("food")
    fun provideFoodHttpClient(): HttpClient = HttpClient(Android) {
        expectSuccess = false // we inspect status ourselves; a non-2xx must not throw
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                }
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 8_000
        }
    }
}
