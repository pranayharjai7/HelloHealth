package com.hellohealth.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSessionManager @Inject constructor(
    private val supabase: SupabaseClient
) {
    suspend fun getCurrentUserId(): String? {
        supabase.auth.awaitInitialization()
        return supabase.auth.currentUserOrNull()?.id
            ?: supabase.auth.currentSessionOrNull()?.user?.id
            ?: runCatching {
                supabase.auth.retrieveUserForCurrentSession(updateSession = true).id
            }.getOrNull()
    }
}
