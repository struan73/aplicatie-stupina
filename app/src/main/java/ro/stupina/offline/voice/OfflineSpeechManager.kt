package ro.stupina.offline.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vosk.Model
import org.vosk.android.SpeechService
import java.io.File

enum class OfflineEngineMode {
    VOSK_OFFLINE_RO,
    ANDROID_OFFLINE_RECOGNIZER,
    LOCAL_FALLBACK_ONLY
}

data class SpeechAvailabilityReport(
    val mode: OfflineEngineMode,
    val description: String,
    val isFullyOfflineSpeechReady: Boolean
)

/**
 * Manager de recunoaștere vocală offline pentru limba română:
 * 1. Verifică mai întâi prezența modelului local Vosk (ex. `vosk-model-small-ro`) în filesDir/vosk-ro.
 * 2. Verifică disponibilitatea SpeechRecognizer pe dispozitiv cu EXTRA_PREFER_OFFLINE=true.
 * 3. Dacă niciun motor vocal offline nu este instalat, comută automat în modul de alternativă locală
 *    (dictare/comenzi rapide offline + tastatură vocală locală / introducere asistată) fără a bloca utilizatorul.
 */
class OfflineSpeechManager(private val context: Context) {

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var androidRecognizer: SpeechRecognizer? = null
    private var voskService: SpeechService? = null

    fun checkAvailability(): SpeechAvailabilityReport {
        val voskDir = File(context.filesDir, "vosk-model-ro")
        if (voskDir.exists() && voskDir.isDirectory && (voskDir.listFiles()?.isNotEmpty() == true)) {
            return SpeechAvailabilityReport(
                mode = OfflineEngineMode.VOSK_OFFLINE_RO,
                description = "Motor Vosk offline română detectat local (fără internet).",
                isFullyOfflineSpeechReady = true
            )
        }
        val recognizerAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (recognizerAvailable) {
            return SpeechAvailabilityReport(
                mode = OfflineEngineMode.ANDROID_OFFLINE_RECOGNIZER,
                description = "Android SpeechRecognizer disponibil (forțat cu EXTRA_PREFER_OFFLINE = true pentru ro-RO). Descărcați pachetul offline Română din Setările Android.",
                isFullyOfflineSpeechReady = true
            )
        }
        return SpeechAvailabilityReport(
            mode = OfflineEngineMode.LOCAL_FALLBACK_ONLY,
            description = "Nu există motor vocal offline instalat pe telefon. Se folosește alternativa locală offline (șabloane vocale rapide și introducere directă).",
            isFullyOfflineSpeechReady = false
        )
    }

    fun startListening(onFinalResult: (String) -> Unit) {
        _errorMessage.value = null
        _partialText.value = ""
        val availability = checkAvailability()

        when (availability.mode) {
            OfflineEngineMode.VOSK_OFFLINE_RO -> startVosk(onFinalResult)
            OfflineEngineMode.ANDROID_OFFLINE_RECOGNIZER -> startAndroidOfflineRecognizer(onFinalResult)
            OfflineEngineMode.LOCAL_FALLBACK_ONLY -> {
                _isListening.value = false
                _errorMessage.value = availability.description
            }
        }
    }

    private fun startVosk(onFinalResult: (String) -> Unit) {
        try {
            val modelDir = File(context.filesDir, "vosk-model-ro")
            val model = Model(modelDir.absolutePath)
            val recognizer = org.vosk.Recognizer(model, 16000.0f)
            voskService = SpeechService(recognizer, 16000.0f).also { service ->
                _isListening.value = true
                service.startListening(object : org.vosk.android.RecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        _partialText.value = extractVoskField(hypothesis, "partial")
                    }

                    override fun onResult(hypothesis: String?) {
                        val text = extractVoskField(hypothesis, "text")
                        if (text.isNotBlank()) {
                            _isListening.value = false
                            service.stop()
                            onFinalResult(text)
                        }
                    }

                    override fun onFinalResult(hypothesis: String?) {
                        val text = extractVoskField(hypothesis, "text")
                        _isListening.value = false
                        if (text.isNotBlank()) onFinalResult(text)
                    }

                    override fun onError(exception: Exception?) {
                        _isListening.value = false
                        _errorMessage.value = "Eroare motor local Vosk: ${exception?.message}. Folosiți alternativa locală."
                    }

                    override fun onTimeout() {
                        _isListening.value = false
                    }
                })
            }
        } catch (e: Exception) {
            _isListening.value = false
            _errorMessage.value = "Nu s-a putut porni modelul Vosk offline: ${e.message}"
        }
    }

    private fun startAndroidOfflineRecognizer(onFinalResult: (String) -> Unit) {
        try {
            androidRecognizer?.destroy()
            androidRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _errorMessage.value = when (error) {
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Motorul Android a cerut internet deoarece pachetul offline Română (ro-RO) lipsește. Instalați pachetul offline în Setări Android sau folosiți alternativa locală."
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "Nu s-a recunoscut textul. Încercați din nou mai aproape de microfon."
                            else -> "Recunoaștere oprită (cod $error). Alternativa locală rămâne activă."
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val best = matches?.firstOrNull().orEmpty()
                        if (best.isNotBlank()) {
                            onFinalResult(best)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        _partialText.value = matches?.firstOrNull().orEmpty()
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ro-RO")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            androidRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            _errorMessage.value = "SpeechRecognizer indisponibil offline: ${e.message}"
        }
    }

    fun stopListening() {
        _isListening.value = false
        try {
            androidRecognizer?.stopListening()
            voskService?.stop()
        } catch (_: Exception) {
        }
    }

    fun release() {
        stopListening()
        androidRecognizer?.destroy()
        androidRecognizer = null
        voskService?.shutdown()
        voskService = null
    }

    private fun extractVoskField(json: String?, key: String): String {
        if (json.isNullOrBlank()) return ""
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(json)?.groupValues?.get(1).orEmpty().trim()
    }
}
