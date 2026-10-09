package app.yamy.bench

/** Mesures d'une génération, destinées au tableau de performance du pitch. */
data class BenchResult(
    val backend: String,
    val loadMs: Long,
    val firstChunkMs: Long,
    val totalMs: Long,
    val chunks: Int,
    val chars: Int,
    val batteryTempBeforeC: Float,
    val batteryTempAfterC: Float,
    val nativeHeapMb: Long,
    val reply: String,
) {
    val charsPerSecond: Double
        get() = if (totalMs > firstChunkMs) chars * 1000.0 / (totalMs - firstChunkMs) else 0.0

    fun summary(): String = buildString {
        appendLine("Backend : $backend")
        appendLine("Chargement : ${loadMs} ms")
        appendLine("Premier morceau : ${firstChunkMs} ms")
        appendLine("Total : ${totalMs} ms (${chunks} morceaux, ${chars} car.)")
        appendLine("Débit : ${"%.1f".format(charsPerSecond)} car./s")
        appendLine("Temp. batterie : ${batteryTempBeforeC}°C → ${batteryTempAfterC}°C")
        appendLine("Mémoire native : ${nativeHeapMb} Mo")
        append("Réponse : $reply")
    }
}
