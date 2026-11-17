package ro.stupina.offline.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsConfirmManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale("ro", "RO"))
                isReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun speakConfirmation(message: String) {
        if (isReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "stupina_tts_${System.currentTimeMillis()}")
        }
    }

    fun isRomanianAvailable(): Boolean = isReady

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
