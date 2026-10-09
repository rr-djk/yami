package app.yamy.mcp.discord

import app.yamy.connect.NetworkGate
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

/** Un salon texte où le bot peut écrire. Les noms sont nettoyés par [DiscordNames.clean] dès leur arrivée. */
data class DiscordChannel(val id: String, val name: String, val guild: String) {
    val label: String get() = "$guild › #$name"
}

/**
 * Les noms de serveurs et de salons sont choisis par n'importe qui : ce sont des données non fiables.
 * On retire tout caractère invisible ou de contrôle, on borne la longueur et on signale les écritures mêlées.
 */
object DiscordNames {
    fun clean(raw: String, max: Int): String =
        raw.replace(Regex("[\\p{C}\\p{Zl}\\p{Zp}\\u3164\\u115F\\u1160\\uFFA0\\u2800]"), "")
            .replace(Regex("[^\\p{L}\\p{N} _'-]+"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim(' ', '-')
            .take(max)
            .ifEmpty { "sans-nom" }

    /** Vrai si le texte mêle plusieurs écritures (ex. « е » cyrillique dans « général ») ou n'est pas en latin. */
    fun looksSuspicious(text: String): Boolean {
        val scripts = text.filter { it.isLetter() }
            .map { Character.UnicodeScript.of(it.code) }
            .filter { it != Character.UnicodeScript.COMMON && it != Character.UnicodeScript.INHERITED }
            .toSet()
        return scripts.size > 1 || (scripts.size == 1 && Character.UnicodeScript.LATIN !in scripts)
    }

    fun isSnowflake(id: String) = id.isNotEmpty() && id.length <= 20 && id.all { it in '0'..'9' }
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
                .map {
                    DiscordChannel(it.getString("id"), DiscordNames.clean(it.getString("name"), 32), DiscordNames.clean(guild.getString("name"), 40))
                }
                .filter { DiscordNames.isSnowflake(it.id) }
        }
    }

    override fun send(channelId: String, content: String): String {
        require(DiscordNames.isSnowflake(channelId)) { "Identifiant de salon invalide" }
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
            // Le jeton ne doit jamais suivre une redirection hors de discord.com.
            instanceFollowRedirects = false
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
