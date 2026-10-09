package app.yamy.privacy

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import java.io.File

/** Une vérification de confidentialité, calculée en direct sur l'appareil. */
data class PrivacyCheck(val label: String, val passed: Boolean, val detail: String)

/** « Ne nous croyez pas sur parole, vérifiez » : relit l'état réel de l'app, rien n'est codé en dur. */
class PrivacyAudit(
    private val context: Context,
    private val box: KeystoreSecretBox,
    private val lock: AppLock,
) {
    fun run(windowFlags: Int): List<PrivacyCheck> {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions.orEmpty().map { it.substringAfterLast('.') }
        val hasInternet = permissions.contains("INTERNET")
        val backupAllowed = (context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val leftovers = File(context.cacheDir, "camera").list().orEmpty().isNotEmpty() ||
            context.getDatabasePath("yamy.db").exists()
        val encrypted = box.hasKey() && !leftovers
        return listOf(
            PrivacyCheck(
                "Aucun accès à Internet",
                !hasInternet,
                if (hasInternet) "Permission INTERNET déclarée" else "Permission INTERNET absente du manifeste",
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
