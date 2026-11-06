package com.atharva.phoneagent

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * VOICE — turns text back into speech.
 *
 * WHAT: a thin wrapper around Android's built-in TextToSpeech engine.
 * WHY: an agent you have to *read* is just an app. Speaking closes the
 * loop for hands-free use — the whole point for our target user.
 * The speaker always talks in the currently selected language.
 */
class Speaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    /** Locale matching the STT language, e.g. Locale("hi","IN"). */
    var locale: Locale = Locale("en", "IN")
        set(value) {
            field = value
            if (ready) tts?.language = value
        }

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.language = locale
            }
        }
    }

    /** Speak now, interrupting anything currently being spoken. */
    fun say(text: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent-utterance")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
