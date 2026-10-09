package app.yamy.engine

import android.content.Context
import java.io.File

/**
 * Emplacement du modèle. Il est copié via `adb push` (jamais téléchargé par
 * l'app, qui n'a pas la permission INTERNET).
 */
class ModelLocator(private val context: Context) {
    val modelFile: File
        get() = File(context.getExternalFilesDir(null), MODEL_FILE_NAME)

    fun isPresent(): Boolean = modelFile.isFile && modelFile.length() > 0

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
    }
}
