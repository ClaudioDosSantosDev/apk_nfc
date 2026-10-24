package com.example.apknfc.qr

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Contrato do registro de presença por QR code (celular SEM NFC).
 *
 * POST /presence/qr
 *   Cabeçalho: Authorization: Bearer <token recebido no login>
 *   Corpo:     { "nonce": "<igual ao do QR>", "totemId": "T01", "signature": "<Base64 DER>" }
 *   Resposta:  JSON (pode ser {}). Os campos são só informativos.
 *
 * O backend deve:
 *  1. validar o token (assim descobre o userId);
 *  2. conferir que o nonce foi emitido para esse totem, não foi usado e não expirou;
 *  3. conferir que o dispositivo do usuário está aprovado;
 *  4. verificar a assinatura ECDSA com SHA-256 sobre (bytes do nonce + userId em UTF-8),
 *     usando a chave pública aprovada no login (mesma regra do NFC);
 *  5. registrar a presença e invalidar o nonce.
 *
 * Códigos HTTP que o app mostra ao usuário:
 *   200 ou 201 -> presença registrada
 *   401        -> token inválido ou expirado
 *   403        -> dispositivo não aprovado ou revogado
 *   404 ou 410 -> nonce desconhecido ou expirado
 *   409        -> nonce já usado
 *   422        -> assinatura inválida
 */
interface PresenceApi {

    @POST("presence/qr")
    suspend fun registerByQr(
        @Header("Authorization") authorization: String,
        @Body request: QrPresenceRequest
    ): Response<QrPresenceResponse>
}

data class QrPresenceRequest(
    val nonce: String,      // o mesmo texto que veio no QR
    val totemId: String,    // o mesmo que veio no QR
    val signature: String   // assinatura ECDSA em DER, codificada em Base64
)

data class QrPresenceResponse(
    val status: String? = null,
    val registeredAt: String? = null
)
