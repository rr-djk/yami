package app.yamy.mcp

import app.yamy.connect.NetworkGate
import app.yamy.connect.NetworkMode
import app.yamy.connect.OfflineException
import app.yamy.mcp.gmail.EmailSender
import app.yamy.mcp.gmail.GmailMcpServer
import app.yamy.mcp.gmail.SmtpEmailSender
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpGmailTest {

    private val sent = ArrayList<Triple<String, String, String>>()
    private val fakeSender = EmailSender { to, subject, body -> sent += Triple(to, subject, body); "Email envoyé avec succès à $to" }
    private val client = McpClient(InProcessTransport(GmailMcpServer(fakeSender)))

    private fun args(to: String = "lea@example.com", subject: String = "Fiches", body: String = "Voici tes fiches.") =
        JSONObject().put("to", to).put("subject", subject).put("body", body)

    @Test
    fun `initialize répond avec les infos du serveur`() {
        val result = client.initialize()
        assertEquals("gmail-mcp-server", result.getJSONObject("serverInfo").getString("name"))
        assertTrue(result.getJSONObject("capabilities").has("tools"))
    }

    @Test
    fun `tools list expose send_email avec son schéma`() {
        val tool = client.listTools().single()
        assertEquals("send_email", tool.name)
        assertEquals(listOf("to", "subject", "body"), List(3) { tool.inputSchema.getJSONArray("required").getString(it) })
    }

    @Test
    fun `tools call envoie un email valide`() {
        val result = client.callTool("send_email", args())
        assertFalse(result.isError)
        assertEquals(listOf(Triple("lea@example.com", "Fiches", "Voici tes fiches.")), sent)
    }

    @Test
    fun `adresse invalide, sujet multiligne ou corps vide sont refusés sans envoi`() {
        assertTrue(client.callTool("send_email", args(to = "pas-une-adresse")).isError)
        assertTrue(client.callTool("send_email", args(subject = "a\nBcc: espion@example.com")).isError)
        assertTrue(client.callTool("send_email", args(body = "  ")).isError)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `outil ou méthode inconnus donnent une erreur JSON-RPC`() {
        assertTrue(runCatching { client.callTool("delete_inbox", args()) }.exceptionOrNull() is McpException)
        val raw = GmailMcpServer(fakeSender).handle("""{"jsonrpc":"2.0","id":7,"method":"resources/list"}""")
        assertEquals(-32601, JSONObject(raw!!).getJSONObject("error").getInt("code"))
    }

    @Test
    fun `une notification ne reçoit pas de réponse et un JSON cassé est signalé`() {
        val server = GmailMcpServer(fakeSender)
        assertNull(server.handle("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
        assertEquals(-32700, JSONObject(server.handle("{oops")!!).getJSONObject("error").getInt("code"))
    }

    @Test
    fun `en mode hors ligne l'envoi SMTP est refusé avant toute connexion`() {
        val gate = NetworkGate { NetworkMode.OFFLINE }
        val smtp = SmtpEmailSender(gate) { error("les identifiants ne doivent même pas être lus") }
        assertTrue(runCatching { smtp.send("lea@example.com", "s", "b") }.exceptionOrNull() is OfflineException)
        val result = McpClient(InProcessTransport(GmailMcpServer(smtp))).callTool("send_email", args())
        assertTrue(result.isError)
        assertTrue(result.text.contains("hors ligne", ignoreCase = true))
    }
}
