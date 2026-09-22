package com.nexa.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
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
        scheme = "nexa"
        host = "auth"
    }
}

@Serializable
private data class ProfileNameRow(@SerialName("display_name") val displayName: String? = null)
@Serializable
private data class AssistantNameRow(@SerialName("assistant_name") val value: String? = null)
@Serializable
private data class OnboardingRow(@SerialName("onboarding_completed") val value: Boolean = false)
@Serializable
data class AutomationRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    @SerialName("trigger_type") val triggerType: String,
    @SerialName("trigger_config") val triggerConfig: JsonObject = JsonObject(emptyMap()),
    @SerialName("action_type") val actionType: String,
    @SerialName("action_config") val actionConfig: JsonObject = JsonObject(emptyMap()),
    @SerialName("next_run_at") val nextRunAt: String? = null,
    @SerialName("last_run_at") val lastRunAt: String? = null,
)

@Serializable
private data class SubscriptionRow(val status: String? = null, @SerialName("trial_ends_at") val trialEndsAt: String? = null, @SerialName("current_period_end") val currentPeriodEnd: String? = null)

class AuthRepository(private val client: SupabaseClient, private val httpClient: HttpClient) {
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
            client.from("profiles").select(columns = Columns.list("onboarding_completed")) { filter { eq("id", id) } }
                .decodeSingle<OnboardingRow>().value
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
            client.from("profiles").select(columns = Columns.list("assistant_name")) { filter { eq("id", id) } }
                .decodeSingle<AssistantNameRow>().value?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun subscriptionAccess(): Boolean {
        val id = currentUserId ?: return false
        return runCatching {
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
        val token = client.auth.currentAccessTokenOrNull() ?: error("Not authenticated")
        val response = httpClient.post(BuildConfig.SUPABASE_URL + "/functions/v1/remita-initiate") {
            header("Authorization", "Bearer " + token)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("plan", plan) })
        }
        return kotlinx.serialization.json.Json.decodeFromString<RemitaInitResult>(response.bodyAsText())
    }

    suspend fun listAutomations(): List<AutomationRule> {
        val id = currentUserId ?: return emptyList()
        return client.from("automation_rules").select {
            filter { eq("owner_id", id) }
            order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
        }.decodeList<AutomationRule>()
    }

    suspend fun createAutomation(
        name: String,
        intervalSeconds: Long,
        actionType: String,
        actionTitle: String,
        actionBody: String? = null,
        actionPriority: String = "NONE",
    ): Boolean {
        val id = currentUserId ?: return false
        val next = java.time.Instant.now().plusSeconds(intervalSeconds).toString()
        client.from("automation_rules").insert(buildJsonObject {
            put("owner_id", id)
            put("name", name.trim())
            put("enabled", true)
            put("trigger_type", "RECURRING")
            put("trigger_config", buildJsonObject { put("interval_seconds", intervalSeconds) })
            put("action_type", actionType)
            put("action_config", buildJsonObject {
                put("title", actionTitle.trim())
                actionBody?.let { put("body", it) }
                put("priority", actionPriority)
                put("timezone", java.time.ZoneId.systemDefault().id)
            })
            put("next_run_at", next)
        })
        return true
    }

    suspend fun setAutomationEnabled(id: String, enabled: Boolean) {
        val owner = currentUserId ?: return
        client.from("automation_rules").update({ set("enabled", enabled) }) {
            filter { eq("id", id); eq("owner_id", owner) }
        }
    }

    suspend fun deleteAutomation(id: String) {
        val owner = currentUserId ?: return
        client.from("automation_rules").delete {
            filter { eq("id", id); eq("owner_id", owner) }
        }
    }

    suspend fun signInWithPassword(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    // No confirmation link, no OTP -- the account is auto-confirmed
    // server-side (migration 019_auto_confirm_signup_no_otp, a BEFORE INSERT
    // trigger on auth.users). This returns an active session immediately.
    suspend fun createAccount(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun sendPasswordResetEmail(email: String) {
        client.auth.resetPasswordForEmail(email)
    }

    suspend fun updatePassword(password: String) {
        client.auth.updateUser { this.password = password }
    }

    suspend fun signOut() = client.auth.signOut()
}
