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
data class AiChatRequest(
    val message: String? = null,
    val chat_id: String? = null,
    val audio_base64: String? = null,
    val audio_mime_type: String? = null,
    val speak: Boolean = false,
)

@Serializable
data class AiChatResponse(
    val reply: String = "",
    val transcript: String? = null,
    val audio_base64: String? = null,
    val audio_mime_type: String? = null,
    val error: String? = null,
)

/**
 * Calls the authenticated ai-gateway. Gemini credentials never enter the
 * Android app. Voice audio is sent only for the duration of the request and
 * is not written to local storage by this client.
 */
class AiGatewayClient(
    private val supabase: SupabaseClient,
    private val httpClient: HttpClient,
) {
    private suspend fun post(request: AiChatRequest): AiChatResponse {
        val accessToken = supabase.auth.currentAccessTokenOrNull()
            ?: return AiChatResponse(error = "unauthenticated")
        val url = "${BuildConfig.SUPABASE_URL}/functions/v1/ai-gateway"
        val response = httpClient.post(url) {
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(AiChatRequest.serializer(), request))
        }
        return Json.decodeFromString(AiChatResponse.serializer(), response.bodyAsText())
    }

    suspend fun sendMessage(message: String, chatId: String? = null): AiChatResponse =
        post(AiChatRequest(message = message, chat_id = chatId))

    suspend fun sendVoice(
        audioBase64: String,
        mimeType: String = "audio/wav",
        chatId: String? = null,
        speak: Boolean = true,
    ): AiChatResponse = post(
        AiChatRequest(
            chat_id = chatId,
            audio_base64 = audioBase64,
            audio_mime_type = mimeType,
            speak = speak,
        ),
    )
}
