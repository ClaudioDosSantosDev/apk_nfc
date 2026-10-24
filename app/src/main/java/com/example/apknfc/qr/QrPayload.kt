package com.example.apknfc.qr

import android.util.Base64
import org.json.JSONObject

/**
 * Conteúdo do QR code mostrado na tela do totem.
 *
 * Formato (JSON):
 *   {"v":1,"totem":"T01","nonce":"<base64url>"}
 *
 *  - v:     versão do formato (o app só aceita 1)
 *  - totem: identificador do totem que mostrou o QR
 *  - nonce: desafio aleatório (16 a 64 bytes) em Base64 URL-safe, de uso único
 *
 * O app usa SOMENTE esses campos. Ele nunca segue endereços (URLs) que
 * venham no QR: o endereço do servidor é fixo no app.
 */
class QrPayload(
    val totemId: String,
    // Nonce exatamente como veio no QR (Base64 URL-safe). É devolvido ao backend.
    val nonce: String,
    // Os mesmos bytes, já decodificados. É isto que o app assina.
    val nonceBytes: ByteArray
) {
    companion object {
        private const val SUPPORTED_VERSION = 1
        private const val NONCE_MIN = 16
        private const val NONCE_MAX = 64

        /** Devolve o payload, ou null se o QR não for válido. */
        fun parse(raw: String): QrPayload? {
            return try {
                val json = JSONObject(raw)
                if (json.optInt("v", -1) != SUPPORTED_VERSION) return null

                val totemId = json.optString("totem", "")
                val nonce = json.optString("nonce", "")
                if (totemId.isBlank() || nonce.isBlank()) return null

                val nonceBytes = Base64.decode(nonce, Base64.URL_SAFE or Base64.NO_WRAP)
                if (nonceBytes.size !in NONCE_MIN..NONCE_MAX) return null

                QrPayload(totemId, nonce, nonceBytes)
            } catch (e: Exception) {
                // JSON malformado ou Base64 inválido: QR que não é do nosso totem
                null
            }
        }
    }
}
