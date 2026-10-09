package app.yamy.mcp.discord

import app.yamy.connect.NetworkGate
import app.yamy.mcp.InProcessTransport
import app.yamy.mcp.McpClient
import app.yamy.mcp.McpToolResult
import app.yamy.privacy.SecretBox
import java.io.File
import java.text.Normalizer
import org.json.JSONArray
import org.json.JSONObject

/** Message proposé par Gemma ; rien ne part sans la confirmation explicite de la personne. */
data class DiscordDraft(val channel: DiscordChannel, val content: String)

/** Côté modèle : Gemma peut seulement proposer un message (salon désigné par son nom). */
fun interface DiscordDrafts {
    fun propose(channelName: String, content: String): String
}

/** Jeton du bot, chiffré (Keystore). Ce n'est jamais le mot de passe de la personne, et il est révocable. */
class DiscordTokenStore(private val file: File, private val box: SecretBox) {
    @Synchronized
    fun load(): String? = runCatching { String(box.decrypt(file.readBytes()), Charsets.UTF_8) }.getOrNull()

    @Synchronized
    fun save(token: String) {
        file.writeBytes(box.encrypt(token.trim().toByteArray(Charsets.UTF_8)))
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    override fun toString() = "DiscordTokenStore(****)"

    companion object {
        /** Un jeton de bot ressemble à trois blocs séparés par des points, sans espace. */
        fun looksValid(token: String) = token.trim().let { t -> t.length >= 50 && t.count { it == '.' } == 2 && t.none { it.isWhitespace() } }
    }
}

/** Connecteur Discord : client MCP branché sur le serveur Discord, derrière le switch réseau. */
class DiscordConnector(private val store: DiscordTokenStore, gate: NetworkGate) {
    private val client = McpClient(InProcessTransport(DiscordMcpServer(HttpDiscordApi(gate, store::load))))
    private val gate = gate
    @Volatile private var initialized = false

    /** Salons connus depuis le dernier « Tester » ou la dernière actualisation (noms seulement, pas secrets). */
    @Volatile var channels: List<DiscordChannel> = emptyList()
        private set

    val isConfigured: Boolean get() = store.load() != null

    /** Réseau : à appeler hors du thread principal. */
    fun refreshChannels(): List<DiscordChannel> {
        val result = call(DiscordMcpServer.LIST_CHANNELS, JSONObject())
        if (result.isError) throw DiscordException(result.text)
        val array = JSONArray(result.text)
        channels = List(array.length()) { i ->
            array.getJSONObject(i).let { DiscordChannel(it.getString("id"), it.getString("name"), it.getString("guild")) }
        }
        return channels
    }

    /** Envoie un message confirmé. Réseau : à appeler hors du thread principal. */
    fun send(draft: DiscordDraft): McpToolResult =
        call(DiscordMcpServer.SEND_MESSAGE, JSONObject().put("channel_id", draft.channel.id).put("content", draft.content))

    fun forget() {
        channels = emptyList()
        store.clear()
    }

    private fun call(tool: String, args: JSONObject): McpToolResult {
        gate.check()
        if (!initialized) {
            client.initialize()
            initialized = true
        }
        return client.callTool(tool, args)
    }

    companion object {
        /** Retrouve un salon par son nom (« #général », « Général », « Mon serveur › général »), sans tenir compte des accents. */
        fun resolve(name: String, channels: List<DiscordChannel>): DiscordChannel? {
            val wanted = key(name.substringAfterLast('›').substringAfterLast('#'))
            return channels.firstOrNull { key(it.name) == wanted }
        }

        private fun key(text: String) =
            Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}
