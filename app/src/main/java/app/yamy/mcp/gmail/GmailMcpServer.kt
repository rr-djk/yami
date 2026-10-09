package app.yamy.mcp.gmail

import app.yamy.mcp.McpClient
import app.yamy.mcp.McpServerHandler
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Envoi effectif d'un email ; l'implémentation réseau vérifie le switch Hors ligne / Connecté. */
fun interface EmailSender {
    fun send(to: String, subject: String, body: String): String
}

/**
 * Portage Android du serveur rr-djk/gmail-mcp-server : même dispatch JSON-RPC (initialize, tools/list,
 * tools/call), même outil `send_email` et mêmes validations, plus un destinataire libre (`to`).
 */
class GmailMcpServer(private val sender: EmailSender) : McpServerHandler {

    override fun handle(line: String): String? {
        val request = try {
            JSONObject(line)
        } catch (e: JSONException) {
            return error(JSONObject.NULL, PARSE_ERROR, "JSON invalide")
        }
        val id = request.opt("id") ?: return null // notification : pas de réponse
        return when (request.optString("method")) {
            "initialize" -> result(
                id,
                JSONObject()
                    .put("protocolVersion", McpClient.PROTOCOL_VERSION)
                    .put("capabilities", JSONObject().put("tools", JSONObject()))
                    .put("serverInfo", JSONObject().put("name", "gmail-mcp-server").put("version", "1.1.2-android")),
            )
            "tools/list" -> result(id, JSONObject().put("tools", JSONArray().put(toolDescription())))
            "tools/call" -> call(id, request.optJSONObject("params") ?: JSONObject())
            else -> error(id, METHOD_NOT_FOUND, "Méthode inconnue : ${request.optString("method")}")
        }
    }

    private fun call(id: Any, params: JSONObject): String {
        if (params.optString("name") != TOOL_NAME) return error(id, INVALID_PARAMS, "Outil inconnu : ${params.optString("name")}")
        val args = params.optJSONObject("arguments") ?: JSONObject()
        val to = args.optString("to").trim()
        val subject = args.optString("subject").trim()
        val body = args.optString("body")
        val problem = validate(to, subject, body)
        if (problem != null) return result(id, content(problem, isError = true))
        return try {
            result(id, content(sender.send(to, subject, body), isError = false))
        } catch (e: Exception) {
            result(id, content("Échec de l'envoi : ${e.message}", isError = true))
        }
    }

    private fun content(text: String, isError: Boolean) =
        JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text))).put("isError", isError)

    private fun result(id: Any, result: JSONObject) = JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result).toString()

    private fun error(id: Any, code: Int, message: String) =
        JSONObject().put("jsonrpc", "2.0").put("id", id).put("error", JSONObject().put("code", code).put("message", message)).toString()

    companion object {
        const val TOOL_NAME = "send_email"
        const val MAX_SUBJECT_LENGTH = 998
        const val MAX_BODY_LENGTH = 100_000
        private const val PARSE_ERROR = -32700
        private const val METHOD_NOT_FOUND = -32601
        private const val INVALID_PARAMS = -32602
        private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun isValidAddress(address: String) = EMAIL.matches(address.trim())

        /** Mêmes règles que `SendEmailTool`, plus le refus des sauts de ligne dans le sujet (injection d'en-têtes). */
        fun validate(to: String, subject: String, body: String): String? = when {
            !isValidAddress(to) -> "Adresse du destinataire invalide : « $to »"
            subject.isBlank() -> "Le sujet est vide."
            subject.length > MAX_SUBJECT_LENGTH -> "Le sujet dépasse $MAX_SUBJECT_LENGTH caractères."
            '\n' in subject || '\r' in subject -> "Le sujet doit tenir sur une ligne."
            body.isBlank() -> "Le message est vide."
            body.length > MAX_BODY_LENGTH -> "Le message dépasse $MAX_BODY_LENGTH caractères."
            else -> null
        }

        fun toolDescription(): JSONObject = JSONObject()
            .put("name", TOOL_NAME)
            .put("description", "Envoie un email en texte simple via Gmail.")
            .put(
                "inputSchema",
                JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put("to", JSONObject().put("type", "string"))
                            .put("subject", JSONObject().put("type", "string"))
                            .put("body", JSONObject().put("type", "string")),
                    )
                    .put("required", JSONArray().put("to").put("subject").put("body")),
            )
    }
}
