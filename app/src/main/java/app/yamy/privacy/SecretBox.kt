package app.yamy.privacy

/** Chiffrement symétrique des données locales ; la clé ne quitte jamais l'appareil. */
interface SecretBox {
    fun encrypt(plain: ByteArray): ByteArray

    /** Lève une exception si les données sont altérées ou chiffrées par une autre clé. */
    fun decrypt(sealed: ByteArray): ByteArray
}
