package com.limitless.codereview.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * TODO (Mukesh): wire this up to a mic button + call `ReviewEngine.review()` on a recognized
 * "review this" command. android.permission.RECORD_AUDIO is already declared in the manifest —
 * still need to request it at runtime (ActivityCompat.requestPermissions) before starting
 * listening.
 *
 * Rough shape:
 *   val trigger = VoiceTrigger(context) { command -> if (command.contains("review", true)) { ... } }
 *   trigger.startListening()
 */
class VoiceTrigger(
    private val context: Context,
    private val onCommand: (String) -> Unit
) {
    private val recognizer: SpeechRecognizer by lazy {
        SpeechRecognizer.createSpeechRecognizer(context)
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?: return
                onCommand(text)
            }

            // TODO: implement the rest of these for real UX (mic level animation, error states)
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    fun destroy() {
        recognizer.destroy()
    }
}
