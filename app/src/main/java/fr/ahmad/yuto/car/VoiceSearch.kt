package fr.ahmad.yuto.car

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.core.content.ContextCompat
import fr.ahmad.yuto.R

/**
 * Recherche vocale : le micro du téléphone écoute, le texte reconnu est envoyé
 * à la recherche YouTube de l'écran de la voiture.
 */
class VoiceSearch(
    private val carContext: CarContext,
    private val onText: (String) -> Unit,
) {

    private var speech: SpeechRecognizer? = null

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle) {
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim()
            if (text.isNullOrEmpty()) toast(R.string.voice_error) else onText(text)
        }

        override fun onError(error: Int) {
            // ERROR_CLIENT arrive aussi quand l'écran est détruit pendant l'écoute
            if (error != SpeechRecognizer.ERROR_CLIENT) toast(R.string.voice_error)
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start() {
        if (ContextCompat.checkSelfPermission(carContext, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toast(R.string.voice_no_permission)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(carContext)) {
            toast(R.string.voice_unavailable)
            return
        }
        val sr = speech ?: SpeechRecognizer.createSpeechRecognizer(carContext).also { speech = it }
        sr.setRecognitionListener(listener)
        sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        })
        toast(R.string.voice_listening)
    }

    fun release() {
        runCatching { speech?.destroy() }
        speech = null
    }

    private fun toast(id: Int) =
        runCatching { CarToast.makeText(carContext, carContext.getString(id), CarToast.LENGTH_LONG).show() }
}
