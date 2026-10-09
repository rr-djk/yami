package app.yamy.persona

/** Source du prompt système (ton et personnalité de Yamy). */
interface PersonaProvider {
    fun systemPrompt(): String
}
