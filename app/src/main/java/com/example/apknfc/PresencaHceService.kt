package com.example.apknfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import com.example.apknfc.presence.KeystoreManager
import com.example.apknfc.presence.PresenceWindow

/**
 * Serviço HCE: responde ao totem (Raspberry Pi + PN532) via NFC.
 *
 * Protocolo (APDUs ISO 7816-4):
 *
 *  1) SELECT AID   00 A4 04 00 <Lc> <AID>
 *       -> 90 00  se a janela de NFC está aberta (biometria confirmada)
 *       -> 69 85  se NÃO está aberta (usuário ainda não confirmou a biometria)
 *
 *  2) SIGN_CHALLENGE   80 10 00 00 <Lc> <nonce de 16 a 64 bytes> [Le]
 *       -> <len userId (1 byte)> <userId UTF-8> <assinatura ECDSA DER> 90 00
 *          A assinatura cobre:  nonce || userId  (SHA256withECDSA)
 *       -> 69 82  se a janela da biometria expirou
 *       -> 69 85  se a janela de NFC não está aberta
 *
 * A janela é de uso único: depois de assinar um desafio, ela fecha.
 */
class PresencaHceService : HostApduService() {

    companion object {
        private const val TAG = "PresencaHceService"

        // Mesmo AID do apduservice.xml e do SELECT enviado pelo totem.
        private val AID = byteArrayOf(
            0xF0.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06
        )

        private const val CLA_PROPRIETARY: Byte = 0x80.toByte()
        private const val INS_SIGN_CHALLENGE: Byte = 0x10

        private const val NONCE_MIN = 16
        private const val NONCE_MAX = 64

        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_SECURITY_NOT_SATISFIED = byteArrayOf(0x69, 0x82.toByte())
        private val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69, 0x85.toByte())
        private val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
        private val SW_ERROR = byteArrayOf(0x6F, 0x00)
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) return SW_WRONG_LENGTH

        return when {
            isSelectAid(commandApdu) -> handleSelect()
            commandApdu[0] == CLA_PROPRIETARY && commandApdu[1] == INS_SIGN_CHALLENGE ->
                handleSignChallenge(commandApdu)
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Conexão HCE desativada. Motivo: $reason")
    }

    private fun handleSelect(): ByteArray =
        if (PresenceWindow.currentUserIdIfArmed() != null) SW_OK else SW_CONDITIONS_NOT_SATISFIED

    private fun handleSignChallenge(apdu: ByteArray): ByteArray {
        val userId = PresenceWindow.currentUserIdIfArmed() ?: return SW_CONDITIONS_NOT_SATISFIED

        // Formato: CLA INS P1 P2 Lc <nonce> [Le]
        if (apdu.size < 5) return SW_WRONG_LENGTH
        val lc = apdu[4].toInt() and 0xFF
        if (lc < NONCE_MIN || lc > NONCE_MAX || apdu.size < 5 + lc) return SW_WRONG_LENGTH
        val nonce = apdu.copyOfRange(5, 5 + lc)

        return try {
            val userIdBytes = userId.toByteArray(Charsets.UTF_8)
            val signature = KeystoreManager.sign(nonce + userIdBytes)

            PresenceWindow.markSent()
            byteArrayOf(userIdBytes.size.toByte()) + userIdBytes + signature + SW_OK
        } catch (e: UserNotAuthenticatedException) {
            Log.w(TAG, "Janela da biometria expirou", e)
            PresenceWindow.markFailed("A confirmação da biometria expirou. Confirme de novo.")
            SW_SECURITY_NOT_SATISFIED
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.w(TAG, "Chave invalidada por mudança de biometria", e)
            KeystoreManager.deleteKey()
            PresenceWindow.markFailed(
                "A biometria do aparelho foi alterada. Saia e entre de novo para refazer o vínculo do dispositivo."
            )
            SW_SECURITY_NOT_SATISFIED
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao assinar o desafio", e)
            PresenceWindow.markFailed("Falha ao assinar o desafio: ${e.message}")
            SW_ERROR
        }
    }

    /** SELECT: 00 A4 04 00 <Lc> <AID>. Confere o AID byte a byte. */
    private fun isSelectAid(apdu: ByteArray): Boolean {
        if (apdu.size < 5 + AID.size) return false
        if (apdu[0] != 0x00.toByte() || apdu[1] != 0xA4.toByte() ||
            apdu[2] != 0x04.toByte() || apdu[3] != 0x00.toByte()
        ) return false
        val lc = apdu[4].toInt() and 0xFF
        if (lc != AID.size) return false
        return apdu.copyOfRange(5, 5 + lc).contentEquals(AID)
    }
}