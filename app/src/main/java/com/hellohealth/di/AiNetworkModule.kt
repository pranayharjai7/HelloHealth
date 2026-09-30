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
 * Dedicated Ktor client for the AI Coaching LLM calls (Gemini / OpenRouter). Kept separate from the
 * Supabase client and the `@Named("food")` nutrition client — mirrors [FoodNetworkModule] exactly,
 * with the same lenient-JSON + non-throwing-status discipline, but with **generous timeouts** because
 * LLM generation is far slower than a food lookup. As with the food client, `expectSuccess = false`
 * so a non-2xx (quota, auth, model-not-found) is inspected by the datasource and mapped to a defined
 * fallback rather than thrown.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiNetworkModule {

    @Provides
    @Singleton
    @Named("ai")
    fun provideAiHttpClient(): HttpClient = HttpClient(Android) {
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
            // LLM generation can take many seconds; be patient but bounded so a hung provider still
            // fails over to the fallback within a reasonable window rather than blocking forever.
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }
}
