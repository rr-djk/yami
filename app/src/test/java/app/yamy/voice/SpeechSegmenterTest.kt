package app.yamy.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpeechSegmenterTest {

    private val segmenter = SpeechSegmenter()
    private val n = segmenter.frameSamples

    private fun silence() = ShortArray(n) { ((it % 7) - 3).toShort() }
    private fun voice() = ShortArray(n) { (4000 * sin(2 * PI * 220 * it / Wav.SAMPLE_RATE)).toInt().toShort() }

    /** Fait passer [frames] trames et renvoie les événements hors niveau sonore. */
    private fun run(frames: List<ShortArray>) =
        frames.flatMap { segmenter.feed(it) }.filter { it !is SegmenterEvent.Level }

    private fun frames(count: Int, make: () -> ShortArray) = List(count) { make() }

    @Test
    fun `une phrase courte suivie d'un silence donne un morceau puis la fin`() {
        val events = run(frames(3, ::silence) + frames(30, ::voice) + frames(16, ::silence))
        assertEquals(2, events.size)
        assertTrue(events[0] is SegmenterEvent.Segment)
        assertEquals(SegmenterEvent.Finished, events[1])
    }

    @Test
    fun `une longue prise de parole est coupée sur une pause pour être transcrite en avance`() {
        val events = run(frames(3, ::silence) + frames(65, ::voice) + frames(6, ::silence) + frames(20, ::voice) + frames(16, ::silence))
        val segments = events.filterIsInstance<SegmenterEvent.Segment>()
        assertEquals(2, segments.size)
        assertEquals(SegmenterEvent.Finished, events.last())
    }

    @Test
    fun `une parole continue est coupée avant la limite de 30 s du modèle`() {
        val events = run(frames(3, ::silence) + frames(260, ::voice))
        val segment = events.filterIsInstance<SegmenterEvent.Segment>().first()
        assertTrue(segment.samples.size <= SpeechSegmenter.MAX_SEGMENT_MS * Wav.SAMPLE_RATE / 1000)
    }

    @Test
    fun `sans parole on s'arrête sans morceau`() {
        val events = run(frames(SpeechSegmenter.NO_SPEECH_TIMEOUT_MS / SpeechSegmenter.FRAME_MS, ::silence))
        assertEquals(listOf(SegmenterEvent.Finished), events)
    }

    @Test
    fun `l'arrêt manuel livre le morceau en cours`() {
        run(frames(3, ::silence) + frames(20, ::voice))
        val events = segmenter.finish()
        assertTrue(events.first() is SegmenterEvent.Segment)
        assertEquals(SegmenterEvent.Finished, events.last())
        assertTrue(segmenter.feed(voice()).isEmpty())
    }

    @Test
    fun `le WAV encodé se relit à l'identique`() {
        val samples = ShortArray(1600) { (it * 3).toShort() }
        val decoded = Wav.decodeToMono(Wav.encode(samples))
        assertTrue(samples.contentEquals(decoded))
    }
}
