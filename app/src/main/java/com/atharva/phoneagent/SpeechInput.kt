package com.atharva.phoneagent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * EARS — turns spoken voice into text.
 *
 * WHAT: a thin wrapper around Android's built-in SpeechRecognizer.
 * WHY this: it is free, already on every Android phone, and understands
 * en-IN / hi-IN / mr-IN. It does NOT understand meaning — it only
 * transcribes sound into words. Understanding happens in Brain.kt.
 * NOTE: on most phones recognition streams to Google's servers,
 * so it needs internet. (Offline Vosk support is a v2 item.)
 */
class SpeechInput(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) : RecognitionListener {

    private val recognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    /** BCP-47 language tag, e.g. "en-IN", "hi-IN", "mr-IN". */
    var languageTag: String = "en-IN"

    init {
        recognizer.setRecognitionListener(this)
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer.startListening(intent)
    }

    fun destroy() {
        recognizer.destroy()
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
        if (text.isNullOrEmpty()) onError("I didn't catch that. Please try again.")
        else onResult(text)
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Network problem — speech recognition needs internet on this phone."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission is missing."
            else -> "Something went wrong while listening (error $error)."
        }
        onError(message)
    }

    // Unused RecognitionListener callbacks.
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
