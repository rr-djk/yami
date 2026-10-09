package app.yamy.voice

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Encodage WAV PCM 16 bits mono, en mémoire (rien n'est écrit sur disque). */
object Wav {
    const val SAMPLE_RATE = 16_000

    fun encode(samples: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataSize = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataSize)
        }
        val body = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { body.putShort(it) }
        return ByteArrayOutputStream(44 + dataSize).apply {
            write(header.array())
            write(body.array())
        }.toByteArray()
    }

    /** Lit un WAV PCM 16 bits (mono ou stéréo) et le ramène en mono à [targetRate] Hz. */
    fun decodeToMono(bytes: ByteArray, targetRate: Int = SAMPLE_RATE): ShortArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var channels = 1
        var rate = targetRate
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = buf.getInt(pos + 4)
            if (id == "fmt ") {
                channels = buf.getShort(pos + 10).toInt()
                rate = buf.getInt(pos + 12)
            }
            if (id == "data") {
                val count = minOf(size, bytes.size - pos - 8) / (2 * channels)
                val mono = ShortArray(count) { i ->
                    var sum = 0
                    for (c in 0 until channels) sum += buf.getShort(pos + 8 + (i * channels + c) * 2)
                    (sum / channels).toShort()
                }
                return resample(mono, rate, targetRate)
            }
            pos += 8 + size + (size and 1)
        }
        return ShortArray(0)
    }

    /** Rééchantillonnage linéaire, suffisant pour de la voix. */
    fun resample(input: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to || input.isEmpty()) return input
        val outSize = (input.size.toLong() * to / from).toInt()
        return ShortArray(outSize) { i ->
            val x = i.toDouble() * from / to
            val i0 = x.toInt().coerceAtMost(input.lastIndex)
            val i1 = (i0 + 1).coerceAtMost(input.lastIndex)
            val t = x - i0
            (input[i0] * (1 - t) + input[i1] * t).toInt().toShort()
        }
    }
}
