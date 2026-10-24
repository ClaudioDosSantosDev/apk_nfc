package com.example.apknfc.qr

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import com.example.apknfc.auth.RetrofitClient
import com.example.apknfc.presence.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado do registro, já pronto para a tela mostrar. */
sealed class QrResult {
    object Success : QrResult()
    data class Failure(val message: String) : QrResult()
}

/**
 * Assina o desafio do QR e envia a presença ao backend.
 */
class QrRepository(private val api: PresenceApi = RetrofitClient.presenceApi) {

    suspend fun register(token: String, userId: String, payload: QrPayload): QrResult =
        withContext(Dispatchers.IO) {

            // 1) Assina (bytes do nonce + userId).
            //    A chave do Keystore só assina logo depois da biometria.
            val signature = try {
                KeystoreManager.sign(payload.nonceBytes + userId.toByteArray(Charsets.UTF_8))
            } catch (e: UserNotAuthenticatedException) {
                return@withContext QrResult.Failure(
                    "A confirmação da biometria expirou. Tente de novo."
                )
            } catch (e: KeyPermanentlyInvalidatedException) {
                // A biometria do aparelho mudou: a chave não vale mais.
                KeystoreManager.deleteKey()
                return@withContext QrResult.Failure(
                    "A biometria do aparelho foi alterada. Saia e entre de novo para refazer o vínculo do dispositivo."
                )
            } catch (e: Exception) {
                return@withContext QrResult.Failure("Falha ao assinar o desafio: ${e.message}")
            }

            // 2) Envia ao backend (contrato em PresenceApi.kt).
            try {
                val response = api.registerByQr(
                    authorization = "Bearer $token",
                    request = QrPresenceRequest(
                        nonce = payload.nonce,
                        totemId = payload.totemId,
                        signature = Base64.encodeToString(signature, Base64.NO_WRAP)
                    )
                )
                if (response.isSuccessful) {
                    QrResult.Success
                } else {
                    QrResult.Failure(messageForHttpCode(response.code()))
                }
            } catch (e: Exception) {
                QrResult.Failure("Não foi possível falar com o servidor: ${e.message}")
            }
        }

    /** Traduz o código HTTP do backend para uma mensagem ao usuário. */
    private fun messageForHttpCode(code: Int): String = when (code) {
        401 -> "Sessão inválida. Saia e entre de novo."
        403 -> "Este aparelho não está aprovado para registrar presença."
        404, 410 -> "QR code expirado ou desconhecido. Escaneie o QR atual do totem."
        409 -> "Este QR code já foi usado."
        422 -> "O servidor recusou a assinatura."
        else -> "Erro do servidor (código $code)."
    }
}
