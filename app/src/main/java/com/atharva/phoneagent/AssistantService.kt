package com.atharva.phoneagent

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService

/**
 * THE ASSISTANT SEAT — the three services Android requires before it
 * will list this app under Settings > Apps > Default apps >
 * Digital assistant app. With the seat, long-press Home summons our
 * agent system-wide instead of the (retired) Google Assistant.
 *
 * WHY three: the system's qualification rules demand all of them —
 * 1) VoiceInteractionService: the entry point the system binds to.
 * 2) VoiceInteractionSessionService: creates the session that receives
 *    each summon. Our session simply opens MainActivity and closes —
 *    v1 keeps all interaction in the activity.
 * 3) RecognitionService: a stub. Real recognition happens in-app via
 *    SpeechRecognizer, but the package does not qualify for the
 *    assistant role without declaring one.
 */
class AssistantService : VoiceInteractionService()

class AssistantSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return object : VoiceInteractionSession(this) {
            override fun onShow(args: Bundle?, showFlags: Int) {
                super.onShow(args, showFlags)
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }
                context.startActivity(intent)
                finish()
            }
        }
    }
}

class StubRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizerErrorClient)
    }

    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}

    private companion object {
        // RecognitionService.Callback expects a SpeechRecognizer error code;
        // ERROR_CLIENT = 5. We never serve real recognition here.
        const val SpeechRecognizerErrorClient = 5
    }
}
