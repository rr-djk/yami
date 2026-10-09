package app.yamy.engine

import app.yamy.mcp.EmailDraft
import app.yamy.mcp.EmailDrafts
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/** Outil exposé à Gemma en mode Connecté : il PRÉPARE un email, la personne confirme l'envoi. */
class EmailToolSet(private val drafts: EmailDrafts) : ToolSet {
    @Tool(description = "Prépare un email que la personne relira et confirmera elle-même avant tout envoi.")
    fun preparerEmail(
        @ToolParam(description = "Adresse email du destinataire") destinataire: String,
        @ToolParam(description = "Sujet de l'email, sur une ligne") sujet: String,
        @ToolParam(description = "Texte de l'email") corps: String,
    ): Map<String, Any> = mapOf("statut" to drafts.propose(EmailDraft(destinataire.trim(), sujet.trim(), corps.trim())))
}
