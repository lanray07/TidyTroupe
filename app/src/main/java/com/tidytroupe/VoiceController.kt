package com.tidytroupe

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Owned by the visible adult voice sheet; no background listening or audio storage. */
class VoiceController(private val context: Context) {
    val available get() = SpeechRecognizer.isRecognitionAvailable(context)
    private var recognizer: SpeechRecognizer? = null
    private var ttsReady = false
    private var tts: TextToSpeech? = null
    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val code = tts?.setLanguage(Locale.UK)
                ttsReady = code != TextToSpeech.LANG_MISSING_DATA && code != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }
    fun speak(text: String): Boolean {
        if (!ttsReady) return false
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tidy-feedback")
        return true
    }
    fun listen(result: (String) -> Unit, error: (String) -> Unit, ended: () -> Unit) {
        if (!available) { error(context.getString(R.string.voice_unavailable)); ended(); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { ended() }
                override fun onError(code: Int) { error("Speech input ended (code $code). Please try again or type your command."); ended() }
                override fun onResults(results: Bundle?) { results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(result); ended() }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        }
    }
    fun close() { recognizer?.cancel(); recognizer?.destroy(); recognizer = null; tts?.stop(); tts?.shutdown() }
}
