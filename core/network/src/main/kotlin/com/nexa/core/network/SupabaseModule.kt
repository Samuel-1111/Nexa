package com.nexa.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

@Serializable
private data class ProfileNameRow(@SerialName("display_name") val displayName: String? = null)

class AuthRepository(private val client: SupabaseClient) {
    val isAuthenticated: Flow<Boolean> = client.auth.sessionStatus.map {
        it is io.github.jan.supabase.auth.status.SessionStatus.Authenticated
    }
    val sessionStatus = client.auth.sessionStatus
    val currentUserId: String? get() = client.auth.currentUserOrNull()?.id
    val currentEmail: String? get() = client.auth.currentUserOrNull()?.email

    suspend fun currentDisplayName(): String? {
        val id = currentUserId ?: return null
        return runCatching {
            client.from("profiles").select(columns = Columns.list("display_name")) {
                filter { eq("id", id) }
            }.decodeSingle<ProfileNameRow>().displayName?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun signInWithEmail(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun requestEmailOtp(email: String, createUser: Boolean = true) {
        client.auth.signInWith(OTP) {
            this.email = email
            this.createUser = createUser
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
