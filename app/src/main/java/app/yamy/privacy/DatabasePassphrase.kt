package app.yamy.privacy

import java.io.File
import java.security.SecureRandom

/**
 * Phrase secrète de la base SQLCipher : 32 octets aléatoires, stockés chiffrés par
 * la clé du Keystore. Sans cette clé, la base est illisible.
 */
class DatabasePassphrase(private val file: File, private val box: SecretBox) {

    @Synchronized
    fun get(): ByteArray {
        if (file.isFile) {
            runCatching { return box.decrypt(file.readBytes()) }
        }
        val fresh = ByteArray(SIZE).also { SecureRandom().nextBytes(it) }
        file.writeBytes(box.encrypt(fresh))
        return fresh
    }

    fun delete() {
        file.delete()
    }

    private companion object {
        const val SIZE = 32
    }
}
