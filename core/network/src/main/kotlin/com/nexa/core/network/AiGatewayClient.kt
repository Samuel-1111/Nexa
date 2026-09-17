package com.nexa.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AiChatRequest(val message: String, val chat_id: String? = null)

@Serializable
data class AiChatResponse(val reply: String = "", val error: String? = null)

/**
 * Calls the ai-gateway Edge Function -- the ONLY place in this app that talks
 * to AI. The Gemini key never enters the Android app; this client sends the
 * user's own Supabase session JWT, which the Edge Function re-verifies
 * server-side before doing anything.
 */
class AiGatewayClient(
    private val supabase: SupabaseClient,
    private val httpClient: HttpClient,
) {
    suspend fun sendMessage(message: String, chatId: String? = null): AiChatResponse {
        val accessToken = supabase.auth.currentAccessTokenOrNull()
            ?: return AiChatResponse(error = "unauthenticated")
        val url = "${BuildConfig.SUPABASE_URL}/functions/v1/ai-gateway"
        val response = httpClient.post(url) {
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(AiChatRequest.serializer(), AiChatRequest(message, chatId)))
        }
        return Json.decodeFromString(AiChatResponse.serializer(), response.bodyAsText())
    }
}
