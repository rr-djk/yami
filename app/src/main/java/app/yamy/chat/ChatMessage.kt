package app.yamy.chat

enum class Role { USER, YAMY }

data class ChatMessage(
    val id: Long = 0,
    val role: Role,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)
