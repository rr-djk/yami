package app.yamy.chat

enum class Role { USER, YAMY }

data class ChatMessage(
    val id: Long = 0,
    val role: Role,
    val text: String,
    /** Une photo accompagnait ce message (elle n'est jamais conservée). */
    val hasImage: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
