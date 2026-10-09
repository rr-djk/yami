package app.yamy.mcp

import org.json.JSONArray
import org.json.JSONObject

data class McpTool(val name: String, val description: String, val inputSchema: JSONObject)

data class McpToolResult(val text: String, val isError: Boolean)

class McpException(message: String) : Exception(message)

/** Client MCP minimal (JSON-RPC 2.0) : initialize, tools/list, tools/call. Indépendant du transport. */
class McpClient(private val transport: McpTransport) {
    private var nextId = 1

    fun initialize(): JSONObject {
        val result = request(
            "initialize",
            JSONObject()
                .put("protocolVersion", PROTOCOL_VERSION)
                .put("capabilities", JSONObject())
                .put("clientInfo", JSONObject().put("name", "yamy").put("version", "0.3.0")),
        )
        transport.send(JSONObject().put("jsonrpc", "2.0").put("method", "notifications/initialized").toString())
        return result
    }

    fun listTools(): List<McpTool> {
        val tools = request("tools/list", JSONObject()).optJSONArray("tools") ?: JSONArray()
        return List(tools.length()) { i ->
            val t = tools.getJSONObject(i)
            McpTool(t.getString("name"), t.optString("description"), t.optJSONObject("inputSchema") ?: JSONObject())
        }
    }

    fun callTool(name: String, arguments: JSONObject): McpToolResult {
        val result = request("tools/call", JSONObject().put("name", name).put("arguments", arguments))
        val content = result.optJSONArray("content") ?: JSONArray()
        val text = List(content.length()) { content.getJSONObject(it).optString("text") }.joinToString("\n")
        return McpToolResult(text, result.optBoolean("isError", false))
    }

    @Synchronized
    private fun request(method: String, params: JSONObject): JSONObject {
        val id = nextId++
        val raw = transport.send(JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params).toString())
            ?: throw McpException("Pas de réponse du serveur MCP à « $method »")
        val response = JSONObject(raw)
        response.optJSONObject("error")?.let { throw McpException(it.optString("message", "Erreur MCP")) }
        if (response.opt("id") != id) throw McpException("Réponse MCP inattendue (id)")
        return response.optJSONObject("result") ?: JSONObject()
    }

    companion object {
        const val PROTOCOL_VERSION = "2024-11-05"
    }
}
