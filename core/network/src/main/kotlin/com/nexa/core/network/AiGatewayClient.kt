package com.nexa.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json

@Serializable
data class AiChatSummary(val id: String, val title: String = "New chat", val updated_at: String = "")

@Serializable
data class AiMessage(val id: String, val role: String, val content: String, val created_at: String = "")

@Serializable
data class AiChatRequest(
    val message: String? = null,
    val chat_id: String? = null,
    val audio_base64: String? = null,
    val audio_mime_type: String? = null,
    val speak: Boolean = false,
    val voice_name: String = "Kore",
    val speech_rate: Float = 1.12f,
)

@Serializable
data class AiChatResponse(
    val chat_id: String? = null,
    val reply: String = "",
    val transcript: String? = null,
    val audio_base64: String? = null,
    val audio_mime_type: String? = null,
    val tool_results: List<JsonElement> = emptyList(),
    val error: String? = null,
)

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
            if (request.audio_base64 != null) header("x-nexa-voice", "1")
            setBody(Json.encodeToString(AiChatRequest.serializer(), request))
        }
        return Json.decodeFromString(AiChatResponse.serializer(), response.bodyAsText())
    }

    suspend fun listChats(): List<AiChatSummary> = runCatching {
        supabase.postgrest.from("chats").select { filter { eq("archived", false) } }
            .decodeList<AiChatSummary>()
    }.getOrDefault(emptyList())

    suspend fun loadMessages(chatId: String): List<AiMessage> = runCatching {
        supabase.postgrest.from("chat_messages").select { filter { eq("chat_id", chatId) } }
            .decodeList<AiMessage>()
    }.getOrDefault(emptyList())

    suspend fun sendMessage(message: String, chatId: String? = null, speak: Boolean = false): AiChatResponse =
        post(AiChatRequest(message = message, chat_id = chatId, speak = speak))

    suspend fun sendVoice(
        audioBase64: String,
        mimeType: String = "audio/wav",
        chatId: String? = null,
        speak: Boolean = true,
        voiceName: String = "Kore",
        speechRate: Float = 1.12f,
    ): AiChatResponse = post(
        AiChatRequest(
            chat_id = chatId,
            audio_base64 = audioBase64,
            audio_mime_type = mimeType,
            speak = speak,
            voice_name = voiceName,
            speech_rate = speechRate,
        ),
    )
}
