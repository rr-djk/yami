package app.yamy.mcp.discord

import android.content.Context
import android.os.SystemClock
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

/**
 * Salons approuvés par la personne : les SEULS que Gemma voit et peut viser. N'importe qui peut inviter un bot
 * public sur son propre serveur ; sans cette liste, ses salons apparaîtraient dans le prompt.
 */
interface ChannelApprovals {
    fun ids(): Set<String>
    fun set(id: String, approved: Boolean)
    fun clear()
}

class PrefsChannelApprovals(context: Context) : ChannelApprovals {
    private val prefs = context.getSharedPreferences("yamy-discord", Context.MODE_PRIVATE)
    override fun ids(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()
    override fun set(id: String, approved: Boolean) {
        prefs.edit().putStringSet(KEY, if (approved) ids() + id else ids() - id).apply()
    }
    override fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "approved"
    }
}

/** Connecteur Discord : client MCP branché sur le serveur Discord, derrière le switch réseau. */
class DiscordConnector(
    private val store: DiscordTokenStore,
    private val gate: NetworkGate,
    private val approvals: ChannelApprovals,
) {
    private val client = McpClient(InProcessTransport(DiscordMcpServer(HttpDiscordApi(gate, store::load))))
    @Volatile private var initialized = false
    @Volatile private var lastRefresh = 0L

    /** Tous les salons trouvés au dernier test (noms nettoyés) ; à approuver un par un. */
    @Volatile var channels: List<DiscordChannel> = emptyList()
        private set

    /** Salons approuvés par la personne : seuls ceux-là sont montrés à Gemma et peuvent être visés. */
    val allowedChannels: List<DiscordChannel> get() = approvals.ids().let { ok -> channels.filter { it.id in ok } }

    val isConfigured: Boolean get() = store.load() != null

    fun isApproved(id: String) = id in approvals.ids()

    fun setApproved(id: String, approved: Boolean) = approvals.set(id, approved)

    /** Réseau : à appeler hors du thread principal. Une seule actualisation toutes les 30 s (sauf [force]). */
    fun refreshChannels(force: Boolean = false): List<DiscordChannel> {
        val now = SystemClock.elapsedRealtime()
        if (!force && channels.isNotEmpty() && now - lastRefresh < MIN_REFRESH_MS) return channels
        val result = call(DiscordMcpServer.LIST_CHANNELS, JSONObject())
        if (result.isError) throw DiscordException(result.text) // la liste précédente est conservée
        val array = JSONArray(result.text)
        channels = List(array.length()) { i ->
            array.getJSONObject(i).let { DiscordChannel(it.getString("id"), it.getString("name"), it.getString("guild")) }
        }
        lastRefresh = now
        return channels
    }

    /** Envoie un message confirmé. Réseau : à appeler hors du thread principal. */
    fun send(draft: DiscordDraft): McpToolResult =
        call(DiscordMcpServer.SEND_MESSAGE, JSONObject().put("channel_id", draft.channel.id).put("content", draft.content))

    /** Retire le bot : jeton, salons connus et approbations. */
    fun forget() {
        channels = emptyList()
        lastRefresh = 0L
        approvals.clear()
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
        private const val MIN_REFRESH_MS = 30_000L

        /**
         * Retrouve UN salon par son nom (« #général », « Mon serveur › général »), sans tenir compte des accents.
         * Le serveur est pris en compte s'il est donné ; un nom vide ou ambigu est refusé (null).
         */
        fun resolve(name: String, channels: List<DiscordChannel>): DiscordChannel? {
            val guild = name.substringBefore('›', "").takeIf { '›' in name }?.let(::key)
            val wanted = key(name.substringAfterLast('›').substringAfterLast('#'))
            if (wanted.isEmpty()) return null
            val matches = channels.filter { key(it.name) == wanted && (guild == null || key(it.guild) == guild) }
            return matches.singleOrNull()
        }

        private fun key(text: String) =
            Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
                .trim('-')
    }
}
