package app.yamy.chat

enum class Role { USER, YAMY }

data class ChatMessage(
    val id: Long = 0,
    val sessionId: Long = 0,
    val role: Role,
    val text: String,
    /** Une photo accompagnait ce message (elle n'est jamais conservée). */
    val hasImage: Boolean = false,
    /** Message dicté au micro (transcrit sur l'appareil, l'audio n'est pas conservé). */
    val viaVoice: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
