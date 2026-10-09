package app.yamy.connect

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hors ligne par défaut. En mode Connecté, seuls les connecteurs (MCP) peuvent sortir du téléphone. */
enum class NetworkMode { OFFLINE, CONNECTED }

/** Mémorise le choix de la personne (valeur non secrète). */
class NetworkModeStore(context: Context) {
    private val prefs = context.getSharedPreferences("yamy-network", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(read())
    val mode: StateFlow<NetworkMode> = _mode.asStateFlow()

    fun set(mode: NetworkMode) {
        prefs.edit().putString(KEY, mode.name).apply()
        _mode.value = mode
    }

    private fun read() = runCatching { NetworkMode.valueOf(prefs.getString(KEY, null) ?: "") }.getOrDefault(NetworkMode.OFFLINE)

    private companion object {
        const val KEY = "mode"
    }
}

class OfflineException : IllegalStateException("Mode hors ligne : aucune connexion n'est autorisée. Active le mode Connecté dans Vie privée.")

/** Seul point de passage vers le réseau : chaque connecteur l'appelle avant toute connexion. */
class NetworkGate(private val mode: () -> NetworkMode) {
    val isOpen: Boolean get() = mode() == NetworkMode.CONNECTED

    fun check() {
        if (!isOpen) throw OfflineException()
    }
}
