package app.yamy.privacy

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Verrou de l'app : empreinte, visage ou code de l'écran de verrouillage.
 * Reverrouille après [GRACE_MS] passées en arrière-plan (la prise de photo ne redemande rien).
 */
class AppLock(private val context: Context) {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var leftAt = 0L
    private var prompting = false

    /** Faux si l'appareil n'a aucun verrouillage d'écran : l'app ne peut alors pas être protégée. */
    val isAvailable: Boolean
        get() = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun onAppLeft() {
        leftAt = SystemClock.elapsedRealtime()
        prompting = false
    }

    /** Horloge monotone : changer la date du téléphone ne prolonge pas le délai de grâce. */
    fun onAppReturned() {
        if (leftAt == 0L) return
        val away = SystemClock.elapsedRealtime() - leftAt
        if (away < 0 || away > GRACE_MS) _locked.value = true
    }

    fun authenticate(activity: FragmentActivity) {
        if (!isAvailable) {
            _locked.value = false
            return
        }
        if (!_locked.value || prompting) return
        prompting = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    _locked.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Yamy")
                .setSubtitle("Déverrouille pour retrouver ta conversation")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        const val GRACE_MS = 30_000L
    }
}
