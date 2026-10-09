package app.yamy.privacy

import java.io.File
import java.security.SecureRandom

/**
 * Phrase secrète de la base SQLCipher : 32 octets aléatoires, stockés chiffrés par
 * la clé du Keystore. Sans cette clé, la base est illisible.
 */
class DatabasePassphrase(private val file: File, private val box: SecretBox) {

    /** Levée quand la phrase existe mais ne peut plus être lue (clé perdue, fichier tronqué). */
    class UnreadableException(cause: Throwable) : Exception("Phrase de la base illisible", cause)

    /** Ne crée une nouvelle phrase que si aucune n'existe : une phrase illisible n'est jamais écrasée en silence. */
    @Synchronized
    fun get(): ByteArray {
        if (file.isFile) {
            return try {
                box.decrypt(file.readBytes())
            } catch (e: Exception) {
                throw UnreadableException(e)
            }
        }
        val fresh = ByteArray(SIZE).also { SecureRandom().nextBytes(it) }
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(box.encrypt(fresh))
        check(temp.renameTo(file)) { "Écriture atomique impossible" }
        return fresh
    }

    fun delete() {
        file.delete()
    }

    private companion object {
        const val SIZE = 32
    }
}
