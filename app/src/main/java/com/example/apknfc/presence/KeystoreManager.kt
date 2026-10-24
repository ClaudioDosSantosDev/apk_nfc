package com.example.apknfc.presence

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Guarda no Android Keystore o par de chaves (EC P-256) que assina o
 * desafio do totem.
 *
 * A chave privada nunca sai do hardware seguro do celular e só pode ser
 * usada durante [AUTH_VALIDITY_SECONDS] segundos depois de uma biometria
 * forte confirmada no BiometricPrompt. A chave pública é enviada ao backend
 * no login, para ser aprovada junto com o vínculo do dispositivo.
 *
 * ATENÇÃO (minSdk): a partir da API 30 a chave aceita SOMENTE biometria forte.
 * Em aparelhos com API 29 ou menos, o Android aceita biometria OU PIN/padrão
 * para liberar a chave. Para o requisito de "biometria obrigatória", use
 * minSdk 30.
 */
object KeystoreManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "presenca_signing_key"

    /**
     * Um pouco maior que PresenceWindow.WINDOW_MS (30 s), para a chave nunca
     * expirar antes da janela de NFC fechar.
     */
    const val AUTH_VALIDITY_SECONDS = 35

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Cria o par de chaves se ainda não existir e devolve a chave pública
     * em Base64 (formato X.509/SubjectPublicKeyInfo, DER), fácil de
     * carregar no backend (ex.: Python `load_der_public_key`).
     *
     * Pode lançar exceção se o aparelho não tiver bloqueio de tela ou
     * biometria cadastrada — quem chama deve tratar.
     */
    fun getOrCreatePublicKeyBase64(): String {
        val ks = keyStore()
        if (!ks.containsAlias(KEY_ALIAS)) generateKeyPair()
        val publicKey = ks.getCertificate(KEY_ALIAS).publicKey
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    private fun generateKeyPair() {
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            // Se o usuário cadastrar uma nova digital/rosto, a chave é invalidada
            // (evita que alguém adicione a própria biometria e use a chave).
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                AUTH_VALIDITY_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
        }

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            .apply { initialize(builder.build()) }
            .generateKeyPair()
    }

    /**
     * Assina [data] com SHA256withECDSA (assinatura em DER).
     *
     * Lança:
     * - UserNotAuthenticatedException: a janela da biometria expirou;
     * - KeyPermanentlyInvalidatedException: a biometria do aparelho mudou.
     */
    fun sign(data: ByteArray): ByteArray {
        val privateKey = keyStore().getKey(KEY_ALIAS, null) as? PrivateKey
            ?: throw IllegalStateException("Chave de assinatura não encontrada")
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    fun deleteKey() {
        val ks = keyStore()
        if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
    }
}
