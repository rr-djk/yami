package app.yamy.vision

import android.net.Uri

/** Transforme une image choisie par l'utilisateur en octets prêts pour le modèle. */
interface ImageSource {
    /** Décode, redimensionne et ré-encode l'image, uniquement en mémoire. */
    suspend fun load(uri: Uri): ByteArray
}
