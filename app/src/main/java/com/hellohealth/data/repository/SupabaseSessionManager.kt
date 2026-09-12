package com.hellohealth.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class SupabaseSessionManager @Inject constructor(
    private val supabase: SupabaseClient?
) {
    open suspend fun getCurrentUserId(): String? {
        val client = supabase ?: return null
        client.auth.awaitInitialization()
        return client.auth.currentUserOrNull()?.id
            ?: client.auth.currentSessionOrNull()?.user?.id
            ?: runCatching {
                client.auth.retrieveUserForCurrentSession(updateSession = true).id
            }.getOrNull()
    }
}
