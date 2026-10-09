package app.yamy.persona

import android.content.Context
import java.io.File

/**
 * Lit `PERSONA.md`. Une copie modifiée par l'utilisateur dans le stockage
 * interne de l'app prime sur la version embarquée dans les assets.
 */
class AssetPersonaProvider(private val context: Context) : PersonaProvider {
    private val override: File get() = File(context.filesDir, FILE_NAME)

    override fun systemPrompt(): String =
        if (override.isFile) {
            override.readText()
        } else {
            context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }
        }

    companion object {
        const val FILE_NAME = "PERSONA.md"
    }
}
