package app.yamy.voice

import kotlin.math.sqrt

/** Ce que le découpeur signale après chaque trame audio. */
sealed interface SegmenterEvent {
    /** Niveau sonore normalisé (0..1), pour l'onde animée. */
    data class Level(val value: Float) : SegmenterEvent

    /** Un morceau de parole prêt à être transcrit pendant que la personne continue de parler. */
    data class Segment(val samples: ShortArray) : SegmenterEvent

    /** Fin de la prise de parole (silence prolongé, durée maximale ou arrêt manuel). */
    data object Finished : SegmenterEvent
}

/**
 * Découpe la voix en morceaux sur les silences. Logique pure (sans Android), testable sur JVM.
 *
 * Mesure sur Pixel 10a : chaque transcription coûte ~3 s fixes, donc on ne découpe pas en petits
 * morceaux ; on coupe seulement les longs messages, sur une pause, pour transcrire pendant que la
 * personne parle encore.
 */
class SpeechSegmenter(private val sampleRate: Int = Wav.SAMPLE_RATE) {
    val frameSamples = sampleRate * FRAME_MS / 1000

    private val buffer = ArrayList<Short>()
    private var noiseFloor = Double.MAX_VALUE
    private var calibrationFrames = 0
    private var segmentHasSpeech = false
    private var everSpoke = false
    private var silenceMs = 0
    private var totalMs = 0
    private var finished = false

    /** Durée enregistrée, en millisecondes. */
    val elapsedMs: Int get() = totalMs

    fun feed(frame: ShortArray): List<SegmenterEvent> {
        if (finished) return emptyList()
        val events = ArrayList<SegmenterEvent>(2)
        val rms = rms(frame)
        if (calibrationFrames < CALIBRATION_FRAMES) {
            noiseFloor = minOf(noiseFloor, rms)
            calibrationFrames++
        }
        val threshold = maxOf(noiseFloor * NOISE_FACTOR, MIN_SPEECH_RMS)
        val speaking = rms > threshold

        frame.forEach { buffer.add(it) }
        totalMs += FRAME_MS
        if (speaking) {
            segmentHasSpeech = true
            everSpoke = true
            silenceMs = 0
        } else {
            silenceMs += FRAME_MS
        }
        events += SegmenterEvent.Level((rms / FULL_SCALE_RMS).toFloat().coerceIn(0f, 1f))

        val bufferMs = buffer.size * 1000 / sampleRate
        when {
            everSpoke && silenceMs >= END_SILENCE_MS -> events += finish()
            totalMs >= MAX_TOTAL_MS -> events += finish()
            !everSpoke && totalMs >= NO_SPEECH_TIMEOUT_MS -> events += finish()
            segmentHasSpeech && bufferMs >= MIN_SEGMENT_MS && silenceMs >= PAUSE_MS -> events += cut()
            bufferMs >= MAX_SEGMENT_MS -> events += cut()
        }
        return events
    }

    /** Arrêt manuel : livre le dernier morceau s'il contient de la parole. */
    fun finish(): List<SegmenterEvent> {
        if (finished) return emptyList()
        finished = true
        return cut() + SegmenterEvent.Finished
    }

    private fun cut(): List<SegmenterEvent> {
        val out = if (segmentHasSpeech && buffer.isNotEmpty()) listOf(SegmenterEvent.Segment(buffer.toShortArray())) else emptyList()
        buffer.clear()
        segmentHasSpeech = false
        return out
    }

    private fun rms(frame: ShortArray): Double {
        if (frame.isEmpty()) return 0.0
        var sum = 0.0
        frame.forEach { sum += it.toDouble() * it }
        return sqrt(sum / frame.size)
    }

    companion object {
        const val FRAME_MS = 100
        const val CALIBRATION_FRAMES = 3
        const val NOISE_FACTOR = 3.0
        const val MIN_SPEECH_RMS = 500.0
        const val FULL_SCALE_RMS = 6000.0
        const val PAUSE_MS = 500
        const val END_SILENCE_MS = 1500
        const val MIN_SEGMENT_MS = 6000
        /** Sous la limite de 30 s de Gemma 4 pour un extrait audio. */
        const val MAX_SEGMENT_MS = 25_000
        const val MAX_TOTAL_MS = 60_000
        const val NO_SPEECH_TIMEOUT_MS = 8000
    }
}
