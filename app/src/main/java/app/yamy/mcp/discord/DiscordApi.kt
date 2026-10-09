package app.yamy.mcp.discord

import app.yamy.connect.NetworkGate
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

/** Un salon texte où le bot peut écrire. */
data class DiscordChannel(val id: String, val name: String, val guild: String) {
    val label: String get() = "$guild › #$name"
}

/** Ce dont le serveur MCP Discord a besoin ; l'implémentation réseau est [HttpDiscordApi]. */
interface DiscordApi {
    fun textChannels(): List<DiscordChannel>
    fun send(channelId: String, content: String): String
}

class DiscordException(message: String) : IOException(message)

/**
 * API REST de Discord (v10) avec un jeton de bot. HTTPS avec vérification du nom d'hôte (comportement par
 * défaut de [HttpsURLConnection]), délais de 10 s, et contrôle du switch réseau AVANT toute connexion.
 */
class HttpDiscordApi(
    private val gate: NetworkGate,
    private val token: () -> String?,
) : DiscordApi {

    override fun textChannels(): List<DiscordChannel> {
        val guilds = JSONArray(request("GET", "/users/@me/guilds"))
        return (0 until guilds.length()).flatMap { i ->
            val guild = guilds.getJSONObject(i)
            val channels = JSONArray(request("GET", "/guilds/${guild.getString("id")}/channels"))
            (0 until channels.length()).map { channels.getJSONObject(it) }
                .filter { it.optInt("type") == TEXT || it.optInt("type") == ANNOUNCEMENT }
                .sortedBy { it.optInt("position") }
                .map { DiscordChannel(it.getString("id"), it.getString("name"), guild.getString("name")) }
        }
    }

    override fun send(channelId: String, content: String): String {
        require(channelId.all { it.isDigit() }) { "Identifiant de salon invalide" }
        val body = JSONObject()
            .put("content", content)
            // Aucun @everyone, @here ni mention : un texte injecté ne peut pas notifier tout un serveur.
            .put("allowed_mentions", JSONObject().put("parse", JSONArray()))
        request("POST", "/channels/$channelId/messages", body.toString())
        return "Message publié."
    }

    private fun request(method: String, path: String, body: String? = null): String {
        gate.check()
        val bot = token() ?: throw DiscordException("Aucun jeton de bot Discord enregistré.")
        val connection = (URL(BASE + path).openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bot $bot")
            setRequestProperty("User-Agent", "DiscordBot (https://github.com/rr-djk/yami, 0.3.0)")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            body?.let { connection.outputStream.use { out -> out.write(it.toByteArray(Charsets.UTF_8)) } }
            val code = connection.responseCode
            if (code in 200..299) return connection.inputStream.bufferedReader().use { it.readText() }
            val retry = connection.getHeaderField("Retry-After")
            throw DiscordException(
                when (code) {
                    401 -> "Discord a refusé le jeton (invalide ou révoqué)."
                    403 -> "Le bot n'a pas le droit d'écrire ici (permission « Envoyer des messages »)."
                    404 -> "Salon introuvable, ou le bot n'est pas sur ce serveur."
                    429 -> "Discord limite le débit : réessaie dans ${retry ?: "quelques"} s."
                    else -> "Discord a répondu $code."
                },
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE = "https://discord.com/api/v10"
        const val TIMEOUT_MS = 10_000
        const val TEXT = 0
        const val ANNOUNCEMENT = 5
    }
}
