package app.yamy.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Lit l'image via le sélecteur de photos (aucune permission), la réduit à
 * [MAX_EDGE_PX] pour limiter mémoire et temps de calcul, puis l'encode en JPEG.
 * L'orientation EXIF est appliquée par [ImageDecoder]. Rien n'est écrit sur disque.
 */
class ContentResolverImageSource(private val context: Context) : ImageSource {

    override suspend fun load(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > MAX_EDGE_PX) {
                val scale = MAX_EDGE_PX.toFloat() / longEdge
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    companion object {
        const val MAX_EDGE_PX = 896
        private const val JPEG_QUALITY = 90
    }
}
