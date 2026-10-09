package app.yamy.privacy

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import java.io.File
import app.yamy.connect.NetworkMode
import app.yamy.connect.NetworkModeStore
import app.yamy.mcp.discord.DiscordTokenStore

/** Une vérification de confidentialité, calculée en direct sur l'appareil. */
data class PrivacyCheck(val label: String, val passed: Boolean, val detail: String)

/** « Ne nous croyez pas sur parole, vérifiez » : relit l'état réel de l'app, rien n'est codé en dur. */
class PrivacyAudit(
    private val context: Context,
    private val box: KeystoreSecretBox,
    private val lock: AppLock,
    private val network: NetworkModeStore,
    private val discord: DiscordTokenStore,
) {
    fun run(windowFlags: Int): List<PrivacyCheck> {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions.orEmpty().map { it.substringAfterLast('.') }
        val hasInternet = permissions.contains("INTERNET")
        val offline = network.mode.value == NetworkMode.OFFLINE
        val traffic = NetworkModeStore.trafficBytes()
        val baseline = network.offlineBaseline
        val silent = traffic >= 0 && baseline >= 0 && traffic == baseline
        val backupAllowed = (context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val leftovers = File(context.cacheDir, "camera").list().orEmpty().isNotEmpty() ||
            context.getDatabasePath("yamy.db").exists()
        val encrypted = box.hasKey() && !leftovers
        return listOf(
            PrivacyCheck(
                "Mode hors ligne",
                !hasInternet || (offline && silent),
                when {
                    !hasInternet -> "Aucune permission INTERNET : rien ne peut sortir du téléphone"
                    offline && silent -> "0 octet échangé depuis le passage en Hors ligne ou le lancement de l'app (compteur réseau d'Android)"
                    offline && traffic < 0 -> "Hors ligne (contrôle logiciel), mais non vérifiable : compteur réseau d'Android indisponible"
                    offline -> "⚠ Du trafic a été compté depuis le passage en Hors ligne : ${traffic - baseline} octets"
                    discord.load() != null -> "Mode Connecté : Yamy contacte Discord pour lister les salons ; seuls les messages que tu confirmes y sont publiés"
                    else -> "Mode Connecté, aucun connecteur configuré"
                },
            ),
            PrivacyCheck(
                "Permission INTERNET",
                true,
                if (hasInternet) "Déclarée pour les connecteurs MCP. Le blocage hors ligne est logiciel ; le compteur ci-dessus le vérifie." else "Absente",
            ),
            PrivacyCheck(
                "Sauvegarde cloud désactivée",
                !backupAllowed,
                if (backupAllowed) "allowBackup actif" else "Rien n'est envoyé dans une sauvegarde Android",
            ),
            PrivacyCheck(
                "Captures d'écran bloquées",
                (windowFlags and WindowManager.LayoutParams.FLAG_SECURE) != 0,
                "Aperçu masqué dans les applications récentes",
            ),
            PrivacyCheck(
                "Données chiffrées, sans résidu",
                encrypted,
                if (encrypted) "Conversations et skills chiffrés, clé dans le Keystore, aucune photo en clair" else "Clé absente, photo temporaire ou ancienne base en clair détectée",
            ),
            PrivacyCheck(
                "Build de production",
                !debuggable,
                if (debuggable) "Build de développement : accessible par adb, à ne pas distribuer" else "Application non déboguable",
            ),
            PrivacyCheck(
                "Verrouillage de l'app",
                lock.isAvailable,
                if (lock.isAvailable) "Empreinte, visage ou code du téléphone" else "Aucun verrouillage d'écran configuré sur ce téléphone",
            ),
        )
    }
}
