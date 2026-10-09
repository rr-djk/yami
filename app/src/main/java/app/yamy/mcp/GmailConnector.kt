package app.yamy.mcp

import app.yamy.connect.NetworkGate
import app.yamy.mcp.gmail.GmailCredentials
import app.yamy.mcp.gmail.GmailMcpServer
import app.yamy.mcp.gmail.SmtpEmailSender
import app.yamy.privacy.SecretBox
import java.io.File
import org.json.JSONObject

/** Brouillon proposé par Gemma ; rien ne part sans la confirmation explicite de la personne. */
data class EmailDraft(val to: String, val subject: String, val body: String)

/** Côté modèle : Gemma peut seulement proposer un brouillon. */
fun interface EmailDrafts {
    fun propose(draft: EmailDraft): String
}

/** Identifiants Gmail chiffrés (Keystore), jamais journalisés. */
class GmailCredentialStore(private val file: File, private val box: SecretBox) {
    @Synchronized
    fun load(): GmailCredentials? = runCatching {
        val json = JSONObject(String(box.decrypt(file.readBytes()), Charsets.UTF_8))
        GmailCredentials(json.getString("user"), json.getString("password"))
    }.getOrNull()

    @Synchronized
    fun save(credentials: GmailCredentials) {
        val json = JSONObject().put("user", credentials.user.trim()).put("password", credentials.appPassword.filterNot { it.isWhitespace() })
        file.writeBytes(box.encrypt(json.toString().toByteArray(Charsets.UTF_8)))
    }

    fun clear() {
        file.delete()
    }
}

/** Connecteur Gmail : client MCP branché sur le serveur Gmail porté, derrière le switch réseau. */
class GmailConnector(private val store: GmailCredentialStore, private val gate: NetworkGate) {
    private val client = McpClient(InProcessTransport(GmailMcpServer(SmtpEmailSender(gate, store::load))))
    @Volatile private var initialized = false

    val isConfigured: Boolean get() = store.load() != null

    /** Envoie un brouillon confirmé. Bloquant (réseau) : à appeler hors du thread principal. */
    fun send(draft: EmailDraft): McpToolResult {
        gate.check()
        if (!initialized) {
            client.initialize()
            initialized = true
        }
        return client.callTool(
            GmailMcpServer.TOOL_NAME,
            JSONObject().put("to", draft.to).put("subject", draft.subject).put("body", draft.body),
        )
    }
}
