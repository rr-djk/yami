package app.yamy.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Capture du micro en 16 kHz mono, uniquement pendant que la personne parle à Yamy.
 * L'audio reste en mémoire : il n'est jamais écrit sur disque.
 */
class VoiceRecorder {
    @Volatile private var stopRequested = false

    /** Demande la fin de l'écoute : le morceau en cours est livré, puis [SegmenterEvent.Finished]. */
    fun stop() {
        stopRequested = true
    }

    /** L'appelant doit avoir obtenu la permission RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    fun record(): Flow<SegmenterEvent> = flow {
        stopRequested = false
        val segmenter = SpeechSegmenter()
        val minBuffer = AudioRecord.getMinBufferSize(Wav.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            Wav.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, segmenter.frameSamples * 4),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Micro indisponible" }
        try {
            recorder.startRecording()
            val frame = ShortArray(segmenter.frameSamples)
            while (coroutineContext.isActive) {
                if (stopRequested) {
                    segmenter.finish().forEach { emit(it) }
                    break
                }
                var filled = 0
                while (filled < frame.size) {
                    val read = recorder.read(frame, filled, frame.size - filled)
                    if (read <= 0) break
                    filled += read
                }
                if (filled < frame.size) break
                val events = segmenter.feed(frame.copyOf())
                events.forEach { emit(it) }
                if (events.any { it is SegmenterEvent.Finished }) break
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)
}
