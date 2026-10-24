package com.example.apknfc.presence

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Encapsula o BiometricPrompt do Android.
 *
 * A leitura e a comparação da digital/rosto acontecem dentro do sistema
 * operacional (hardware seguro). O app só recebe "autenticou" ou "falhou":
 * nenhum dado biométrico passa pelo app nem pelo servidor.
 */
object BiometricHelper {

    // Só biometria forte (Classe 3). Não inclui PIN/padrão de desbloqueio.
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
                BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Mostra o prompt de biometria. Ao confirmar, o Android libera a chave do
     * Keystore por KeystoreManager.AUTH_VALIDITY_SECONDS segundos e [onSuccess]
     * é chamado.
     */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val message = when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> "Confirmação cancelada."
                    else -> errString.toString()
                }
                onFailure(message)
            }

            // onAuthenticationFailed (digital não reconhecida): o prompt continua
            // aberto para nova tentativa, então não há nada a fazer aqui.
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirmar presença")
            .setSubtitle("Use sua biometria para liberar o registro por NFC")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setNegativeButtonText("Cancelar")
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
