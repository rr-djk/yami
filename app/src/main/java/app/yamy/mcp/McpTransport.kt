package app.yamy.mcp

/** Une requête JSON-RPC 2.0 (une ligne) vers un serveur MCP ; renvoie la réponse, ou null pour une notification. */
fun interface McpTransport {
    fun send(request: String): String?
}

/** Côté serveur : traite une ligne JSON-RPC, comme la boucle stdio de `McpServer` (gmail-mcp-server). */
fun interface McpServerHandler {
    fun handle(line: String): String?
}

/**
 * Le serveur tourne dans l'app (Android ne lance pas un `java -jar` en stdio) mais on garde
 * exactement le même protocole : des lignes JSON-RPC sérialisées dans les deux sens.
 */
class InProcessTransport(private val server: McpServerHandler) : McpTransport {
    override fun send(request: String): String? = server.handle(request)
}
