package com.nexa.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for the Supabase connection. Built once from
 * BuildConfig values populated at build time from local.properties (see
 * build.gradle.kts) -- never from a hardcoded string.
 *
 * CONFIGURATION REQUIRED: until NEXA_SUPABASE_URL / NEXA_SUPABASE_ANON_KEY are
 * present in local.properties, [SUPABASE_URL] is blank and every call here
 * will fail fast rather than silently hitting an empty host.
 */
fun buildSupabaseClient(): SupabaseClient = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
) {
    install(Postgrest)
    install(Auth)
}

class AuthRepository(private val client: SupabaseClient) {
    val isAuthenticated: Flow<Boolean> = client.auth.sessionStatus.map {
        it is io.github.jan.supabase.auth.status.SessionStatus.Authenticated
    }

    val currentUserId: String? get() = client.auth.currentUserOrNull()?.id

    suspend fun signUpWithEmail(email: String, password: String) {
        client.auth.signUpWith(io.github.jan.supabase.auth.providers.builtin.Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        client.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() = client.auth.signOut()
}
