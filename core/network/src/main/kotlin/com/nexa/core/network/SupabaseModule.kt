package com.nexa.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    suspend fun isOnboardingComplete(): Boolean {
        val id = currentUserId ?: return false
        return runCatching {
            @Serializable data class Row(@SerialName("onboarding_completed") val value: Boolean = false)
            client.from("profiles").select(columns = Columns.list("onboarding_completed")) { filter { eq("id", id) } }
                .decodeSingle<Row>().value
        }.getOrDefault(false)
    }

    suspend fun saveOnboardingProfile(displayName: String, assistantName: String) {
        val id = currentUserId ?: error("Not authenticated")
        client.from("profiles").update({
            set("display_name", displayName.trim())
            set("assistant_name", assistantName.trim().ifBlank { "NEXA" })
            set("onboarding_completed", true)
            set("timezone", java.time.ZoneId.systemDefault().id)
        }) {
            filter { eq("id", id) }
        }
    }

    suspend fun currentAssistantName(): String? {
        val id = currentUserId ?: return null
        return runCatching {
            @Serializable data class Row(@SerialName("assistant_name") val value: String? = null)
            client.from("profiles").select(columns = Columns.list("assistant_name")) { filter { eq("id", id) } }
                .decodeSingle<Row>().value?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun subscriptionAccess(): Boolean {
        val id = currentUserId ?: return false
        return runCatching {
            @Serializable data class SubscriptionRow(
                val status: String? = null,
                @SerialName("trial_ends_at") val trialEndsAt: String? = null,
                @SerialName("current_period_end") val currentPeriodEnd: String? = null,
            )
            val row = client.from("subscriptions").select { filter { eq("user_id", id) } }.decodeSingle<SubscriptionRow>()
            val now = java.time.Instant.now()
            val trialOk = row.status == "TRIALING" && row.trialEndsAt?.let { java.time.Instant.parse(it).isAfter(now) } == true
            val paidOk = row.status == "ACTIVE" && row.currentPeriodEnd?.let { java.time.Instant.parse(it).isAfter(now) } == true
            trialOk || paidOk
        }.getOrDefault(false)
    }

    @Serializable
    data class RemitaInitResult(
        val ok: Boolean = false,
        val plan: String? = null,
        val amount_kobo: Int? = null,
        val rrr: String? = null,
        val order_id: String? = null,
        val error: String? = null,
    )

    suspend fun initiateSubscription(plan: String): RemitaInitResult {
        val response = client.functions.invoke(
            function = "remita-initiate",
            body = buildJsonObject { put("plan", plan) },
        )
        return response.body<RemitaInitResult>()
    }

    suspend fun signInWithEmail(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    // Real signup: creates the account WITH a password (unlike the OTP/magic-link
    // flow, which creates a passwordless account). Supabase emails a 6-digit
    // code as part of its normal "confirm signup" email. Login afterwards
    // uses that same password -- no OTP needed to log back in.
    suspend fun signUpWithEmail(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    // Verifies the code from that signup confirmation email. Uses the SIGNUP
    // type, matching signUpWithEmail above -- not the passwordless OTP type.
    suspend fun verifySignupOtp(email: String, token: String) {
        client.auth.verifyEmailOtp(
            type = OtpType.Email.SIGNUP,
            email = email,
            token = token,
        )
    }

    suspend fun resendSignupOtp(email: String) {
        client.auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    suspend fun sendPasswordResetEmail(email: String) {
        client.auth.resetPasswordForEmail(email)
    }

    suspend fun updatePassword(password: String) {
        client.auth.updateUser { this.password = password }
    }

    suspend fun signOut() = client.auth.signOut()
}
