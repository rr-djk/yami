package app.yamy.memory

import app.yamy.skills.SkillFormat

/**
 * Une note de mémoire, façon Obsidian : un nom court, un résumé d'une ligne (pour l'index chargé
 * dans le prompt) et un contenu Markdown qui peut pointer vers d'autres notes avec `[[nom]]`.
 */
data class MemoryNote(val name: String, val summary: String, val content: String) {
    /** Notes citées dans le contenu (`[[nom]]`). */
    val links: List<String> get() = LINK.findAll(content).map { it.groupValues[1].trim() }.distinct().toList()

    companion object {
        private val LINK = Regex("\\[\\[([^\\]]+)]]")
    }
}

/** Format d'une note (même gabarit qu'un `SKILL.md`) et analyse de la sortie de l'agent Scribe. Logique pure. */
object MemoryFormat {
    const val MAX_SUMMARY = 160
    const val MAX_CONTENT = 2000
    const val MAX_INDEX_LINES = 30

    fun format(note: MemoryNote): String = buildString {
        appendLine("---")
        appendLine("name: ${note.name}")
        appendLine("summary: ${note.summary}")
        appendLine("---")
        appendLine()
        appendLine(note.content.trim())
    }

    fun parse(raw: String): MemoryNote? {
        val lines = raw.trim().lines()
        if (lines.firstOrNull()?.trim() != "---") return null
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end < 0) return null
        val header = lines.subList(1, end + 1).associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        return valid(MemoryNote(header["name"].orEmpty(), header["summary"].orEmpty(), lines.drop(end + 2).joinToString("\n").trim()))
    }

    fun valid(note: MemoryNote): MemoryNote? {
        val name = SkillFormat.slugify(note.name)
        if (name.isEmpty() || note.content.isBlank()) return null
        return MemoryNote(
            name = name,
            summary = note.summary.replace('\n', ' ').trim().take(MAX_SUMMARY).ifEmpty { note.content.lineSequence().first().take(MAX_SUMMARY) },
            // Un lien d'une note vers elle-même n'apporte rien (les petits modèles en produisent).
            content = note.content.replace("[[$name]]", "").replace(Regex(" +([.,])"), "$1").trim().take(MAX_CONTENT),
        )
    }

    /** Index d'une ligne par note, injecté dans le prompt système (chargement progressif). */
    fun index(notes: List<MemoryNote>): String =
        notes.take(MAX_INDEX_LINES).joinToString("\n") { "- [[${it.name}]] : ${it.summary}" }

    /**
     * Sortie attendue du Scribe (une ou plusieurs notes, ou RIEN) :
     * ```
     * NOTE: examens | Stress avant les examens de biologie
     * Révise le soir, a peur d'oublier les définitions. Voir [[maman]].
     * ```
     */
    /** « NOTE: », « NOTE : », « **Note :** », « - NOTE: »… (les petits modèles varient la forme). */
    private val NOTE_LINE = Regex("^[\\s*#>-]*note\\s*\\**\\s*:\\s*\\**(.*)$", RegexOption.IGNORE_CASE)

    fun parseScribe(raw: String): List<MemoryNote> {
        val notes = ArrayList<MemoryNote>()
        var header: String? = null
        val body = StringBuilder()
        fun flush() {
            val h = header ?: return
            val name = h.substringBefore('|').trim()
            val summary = h.substringAfter('|', "").trim()
            // Le modèle met parfois tout sur la ligne NOTE : le résumé sert alors de contenu.
            val content = body.toString().trim().ifEmpty { summary }
            valid(MemoryNote(name, summary, content))?.let { notes += it }
        }
        for (line in raw.lines()) {
            val match = NOTE_LINE.find(line)
            if (match != null) {
                flush()
                header = match.groupValues[1].trim().removeSuffix("**").trim()
                body.clear()
            } else if (header != null) {
                body.appendLine(line)
            }
        }
        flush()
        return notes
    }

    /** Fusionne une nouvelle information dans une note existante sans dupliquer les phrases déjà présentes. */
    fun merge(existing: MemoryNote?, update: MemoryNote): MemoryNote {
        if (existing == null) return update
        val known = existing.content.lines().map { it.trim() }.toSet()
        val added = update.content.lines().map { it.trim() }.filter { it.isNotEmpty() && it !in known }
        val content = (existing.content.trim() + if (added.isEmpty()) "" else "\n" + added.joinToString("\n")).take(MAX_CONTENT)
        return existing.copy(summary = update.summary.ifBlank { existing.summary }, content = content)
    }
}
