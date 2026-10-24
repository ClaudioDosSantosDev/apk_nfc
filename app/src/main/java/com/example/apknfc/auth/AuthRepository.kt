package com.example.apknfc.auth

/**
 * Resultado tratado do login, já traduzido para algo
 * fácil de usar na UI (sem o app precisar entender
 * strings cruas de "status" vindas do backend).
 */
sealed class LoginResult {
    data class Approved(val userId: String, val token: String) : LoginResult()
    data class PendingApproval(val requestId: String, val code: String) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

sealed class StatusResult {
    object StillPending : StatusResult()
    data class Approved(val userId: String, val token: String) : StatusResult()
    object Rejected : StatusResult()
    object Expired : StatusResult()
    data class Error(val message: String) : StatusResult()
}

class AuthRepository(private val api: AuthApi = RetrofitClient.authApi) {

    companion object {
        // TODO: REMOVER antes de publicar/entregar a versão final.
        // Login de debug: entra direto, sem tocar na rede, pra testar
        // a tela de status e o NFC sem depender do backend existir.
        private const val DEBUG_USERNAME = "debug"
        private const val DEBUG_PASSWORD = "debug123"
        private const val DEBUG_USER_ID = "USER-DEBUG"
        private const val DEBUG_TOKEN = "debug-token"
    }

    suspend fun login(
        username: String,
        password: String,
        deviceId: String,
        publicKey: String? = null
    ): LoginResult {
        if (username == DEBUG_USERNAME && password == DEBUG_PASSWORD) {
            return LoginResult.Approved(userId = DEBUG_USER_ID, token = DEBUG_TOKEN)
        }

        return try {
            val response = api.login(LoginRequest(username, password, deviceId, publicKey))
            when (response.status) {
                "approved" -> LoginResult.Approved(
                    userId = response.userId ?: return LoginResult.Error("Resposta sem userId"),
                    token = response.token ?: return LoginResult.Error("Resposta sem token")
                )
                "pending" -> LoginResult.PendingApproval(
                    requestId = response.requestId ?: return LoginResult.Error("Resposta sem requestId"),
                    code = response.code ?: return LoginResult.Error("Resposta sem código")
                )
                else -> LoginResult.Error("Status desconhecido: ${response.status}")
            }
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "Falha ao conectar com o servidor")
        }
    }

    suspend fun checkStatus(requestId: String): StatusResult {
        return try {
            val response = api.checkStatus(requestId)
            when (response.status) {
                "pending" -> StatusResult.StillPending
                "approved" -> StatusResult.Approved(
                    userId = response.userId ?: return StatusResult.Error("Resposta sem userId"),
                    token = response.token ?: return StatusResult.Error("Resposta sem token")
                )
                "rejected" -> StatusResult.Rejected
                "expired" -> StatusResult.Expired
                else -> StatusResult.Error("Status desconhecido: ${response.status}")
            }
        } catch (e: Exception) {
            StatusResult.Error(e.message ?: "Falha ao conectar com o servidor")
        }
    }
}