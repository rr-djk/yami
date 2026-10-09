package app.yamy.mcp.discord

import app.yamy.mcp.McpClient
import app.yamy.mcp.McpServerHandler
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Serveur MCP Discord, exécuté dans l'app (même dispatch JSON-RPC que gmail-mcp-server : initialize,
 * tools/list, tools/call). Outils : `list_channels` et `send_message`.
 */
class DiscordMcpServer(private val api: DiscordApi) : McpServerHandler {

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
                    .put("serverInfo", JSONObject().put("name", "discord-mcp-server").put("version", "0.3.0")),
            )
            "tools/list" -> result(id, JSONObject().put("tools", JSONArray().put(listChannelsTool()).put(sendMessageTool())))
            "tools/call" -> call(id, request.optJSONObject("params") ?: JSONObject())
            else -> error(id, METHOD_NOT_FOUND, "Méthode inconnue : ${request.optString("method")}")
        }
    }

    private fun call(id: Any, params: JSONObject): String {
        val args = params.optJSONObject("arguments") ?: JSONObject()
        return try {
            when (params.optString("name")) {
                LIST_CHANNELS -> {
                    val channels = JSONArray()
                    api.textChannels().forEach {
                        channels.put(JSONObject().put("id", it.id).put("name", it.name).put("guild", it.guild))
                    }
                    result(id, content(channels.toString(), isError = false))
                }
                SEND_MESSAGE -> {
                    val channelId = args.optString("channel_id").trim()
                    val text = args.optString("content")
                    val problem = validate(channelId, text)
                    if (problem != null) result(id, content(problem, isError = true)) else result(id, content(api.send(channelId, text), isError = false))
                }
                else -> error(id, INVALID_PARAMS, "Outil inconnu : ${params.optString("name")}")
            }
        } catch (e: Exception) {
            result(id, content("Échec : ${e.message}", isError = true))
        }
    }

    private fun content(text: String, isError: Boolean) =
        JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text))).put("isError", isError)

    private fun result(id: Any, result: JSONObject) = JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result).toString()

    private fun error(id: Any, code: Int, message: String) =
        JSONObject().put("jsonrpc", "2.0").put("id", id).put("error", JSONObject().put("code", code).put("message", message)).toString()

    companion object {
        const val LIST_CHANNELS = "list_channels"
        const val SEND_MESSAGE = "send_message"
        const val MAX_LENGTH = 2000
        private const val PARSE_ERROR = -32700
        private const val METHOD_NOT_FOUND = -32601
        private const val INVALID_PARAMS = -32602

        /** Caractères invisibles ou de sens d'écriture : ils permettraient de cacher du texte dans la carte de confirmation. */
        private val HIDDEN = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF]|[\\p{Cc}&&[^\\n\\t]]")

        /** Ce qui est affiché est exactement ce qui part : pas de longues suites de lignes vides. */
        fun normalize(text: String) = text.replace("\r\n", "\n").replace(Regex("\n{3,}"), "\n\n").trim()

        fun validate(channelId: String, text: String): String? = when {
            channelId.isEmpty() || !channelId.all { it.isDigit() } -> "Salon invalide."
            text.isBlank() -> "Le message est vide."
            text.length > MAX_LENGTH -> "Le message dépasse $MAX_LENGTH caractères (limite de Discord)."
            HIDDEN.containsMatchIn(text) -> "Caractères invisibles refusés."
            else -> null
        }

        private fun listChannelsTool() = JSONObject()
            .put("name", LIST_CHANNELS)
            .put("description", "Liste les salons texte où le bot Discord peut écrire.")
            .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject()))

        private fun sendMessageTool() = JSONObject()
            .put("name", SEND_MESSAGE)
            .put("description", "Publie un message texte dans un salon Discord.")
            .put(
                "inputSchema",
                JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject()
                            .put("channel_id", JSONObject().put("type", "string"))
                            .put("content", JSONObject().put("type", "string")),
                    )
                    .put("required", JSONArray().put("channel_id").put("content")),
            )
    }
}
