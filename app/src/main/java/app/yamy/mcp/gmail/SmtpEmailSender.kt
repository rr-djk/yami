package app.yamy.mcp.gmail

import app.yamy.connect.NetworkGate
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/** Identifiants Gmail : adresse + mot de passe d'application (jamais le mot de passe du compte). */
data class GmailCredentials(val user: String, val appPassword: String)

/**
 * Envoi SMTP vers Gmail, mêmes réglages que `EmailService` (gmail-mcp-server) : STARTTLS sur le port 587,
 * délais de 10 s. Vérifie le switch réseau AVANT toute connexion.
 */
class SmtpEmailSender(
    private val gate: NetworkGate,
    private val credentials: () -> GmailCredentials?,
) : EmailSender {

    override fun send(to: String, subject: String, body: String): String {
        gate.check()
        val creds = credentials() ?: throw IllegalStateException("Gmail n'est pas configuré.")
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "587")
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(creds.user, creds.appPassword)
        })
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(creds.user))
            setRecipient(Message.RecipientType.TO, InternetAddress(to))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
        }
        Transport.send(message)
        return "Email envoyé avec succès à $to"
    }
}
