package com.example.apknfc.auth

/**
 * Modelos de dados trocados com o backend durante o login
 * e o processo de aprovação do dispositivo.
 */

data class LoginRequest(
    val username: String,
    val password: String,
    val deviceId: String,
    // Chave pública (Base64, X.509 DER, EC P-256) do Keystore do celular.
    // O backend a guarda junto com o vínculo do dispositivo e a usa para
    // verificar a assinatura do desafio do totem.
    val publicKey: String? = null
)

/**
 * O backend pode responder de duas formas ao login:
 * - já aprovado: devolve token + userId direto
 * - pendente: devolve um requestId pra gente consultar o status depois,
 *   além do código que será mostrado na tela pro usuário mostrar ao responsável
 */
data class LoginResponse(
    val status: String,      // "approved" ou "pending"
    val token: String? = null,
    val userId: String? = null,
    val requestId: String? = null,
    val code: String? = null,
    val expiresAt: String? = null
)

data class StatusResponse(
    val status: String,      // "pending", "approved", "rejected" ou "expired"
    val token: String? = null,
    val userId: String? = null
)