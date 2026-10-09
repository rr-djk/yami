package app.yamy.engine

import app.yamy.mcp.discord.DiscordDrafts
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/** Outil exposé à Gemma en mode Connecté : il PRÉPARE un message Discord, la personne confirme l'envoi. */
class DiscordToolSet(private val drafts: DiscordDrafts) : ToolSet {
    @Tool(description = "Prépare un message pour un salon Discord ; la personne le relira et confirmera elle-même avant tout envoi.")
    fun preparerMessage(
        @ToolParam(description = "Nom du salon Discord, tel qu'il apparaît dans la liste des salons disponibles") salon: String,
        @ToolParam(description = "Texte du message") message: String,
    ): Map<String, Any> = mapOf("statut" to drafts.propose(salon, message))
}
