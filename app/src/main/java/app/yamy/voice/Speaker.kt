package app.yamy.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Voix de Yamy. N'utilise QUE des voix installées sur le téléphone : une voix qui demanderait le réseau
 * enverrait le texte au moteur de synthèse en ligne, ce qui casserait la promesse de confidentialité.
 * Sans voix hors ligne, Yamy reste muet.
 */
class Speaker(context: Context) {
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    @Volatile var isAvailable = false
        private set

    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) configure()
        }
    }

    private fun configure() {
        val offline = tts.voices.orEmpty()
            .filter { it.locale.language == Locale.FRENCH.language && !it.isNetworkConnectionRequired }
            .minByOrNull { if (it.locale.country == "FR") 0 else 1 }
        if (offline == null) return
        tts.voice = offline
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                _speaking.value = true
            }

            override fun onDone(id: String?) {
                _speaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                _speaking.value = false
            }
        })
        isAvailable = true
    }

    fun speak(text: String) {
        if (!isAvailable) return
        val clean = text.replace(Regex("[\\p{So}\\p{Cn}*_#]"), "").trim()
        if (clean.isNotEmpty()) tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "yamy-${System.nanoTime()}")
    }

    fun stop() {
        if (isAvailable) tts.stop()
        _speaking.value = false
    }
}
