package com.nexa.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun buildSupabaseClient(): SupabaseClient = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
) {
    install(Postgrest)
    install(Auth) {
        autoLoadFromStorage = true
        alwaysAutoRefresh = true
    }
}

class AuthRepository(private val client: SupabaseClient) {
    val isAuthenticated: Flow<Boolean> = client.auth.sessionStatus.map {
        it is io.github.jan.supabase.auth.status.SessionStatus.Authenticated
    }
    val sessionStatus = client.auth.sessionStatus
    val currentUserId: String? get() = client.auth.currentUserOrNull()?.id
    val currentEmail: String? get() = client.auth.currentUserOrNull()?.email

    suspend fun signInWithEmail(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun requestEmailOtp(email: String) {
        client.auth.signInWith(OTP) {
            this.email = email
        }
    }

    suspend fun verifyEmailOtp(email: String, token: String) {
        client.auth.verifyEmailOtp(
            type = OtpType.Email.EMAIL,
            email = email,
            token = token,
        )
    }

    suspend fun sendPasswordResetEmail(email: String) {
        client.auth.resetPasswordForEmail(email)
    }

    suspend fun updatePassword(password: String) {
        client.auth.updateUser { this.password = password }
    }

    suspend fun signOut() = client.auth.signOut()
}
