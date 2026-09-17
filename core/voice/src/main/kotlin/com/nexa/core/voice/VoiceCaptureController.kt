package com.nexa.core.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface VoiceCaptureState {
    data object Idle : VoiceCaptureState
    data object Listening : VoiceCaptureState
    data class Partial(val text: String) : VoiceCaptureState
    data class Final(val text: String) : VoiceCaptureState
    data class Error(val code: Int) : VoiceCaptureState
}

class VoiceCaptureController(private val context: Context) : RecognitionListener {
    private val mutableState = MutableStateFlow<VoiceCaptureState>(VoiceCaptureState.Idle)
    val state: StateFlow<VoiceCaptureState> = mutableState
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (!isAvailable()) { mutableState.value = VoiceCaptureState.Error(SpeechRecognizer.ERROR_CLIENT); return }
        val useOnDevice = android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        recognizer = if (useOnDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(this)
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
        mutableState.value = VoiceCaptureState.Listening
    }

    fun stop() = recognizer?.stopListening()
    fun release() { recognizer?.destroy(); recognizer = null }
    override fun onPartialResults(results: android.os.Bundle) { mutableState.value = VoiceCaptureState.Partial(results.firstText()) }
    override fun onResults(results: android.os.Bundle) { release(); mutableState.value = VoiceCaptureState.Final(results.firstText()) }
    override fun onError(error: Int) { release(); mutableState.value = VoiceCaptureState.Error(error) }
    override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit

    private fun android.os.Bundle.firstText(): String =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
}
