package com.example.apknfc.qr

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.apknfc.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Estados da tela do fluxo por QR code. */
sealed class QrUiState {
    object Idle : QrUiState()          // nada em andamento
    object Scanning : QrUiState()      // leitor de QR aberto
    object Sending : QrUiState()       // assinando e enviando ao backend
    object Success : QrUiState()       // backend confirmou a presença
    data class Failed(val message: String) : QrUiState()
}

/**
 * Controla o fluxo: biometria -> leitor de QR -> assinatura e envio.
 * A biometria e o leitor são abertos pela MainActivity; este ViewModel
 * recebe os resultados e cuida do envio.
 */
class QrViewModel(
    private val sessionManager: SessionManager,
    private val repository: QrRepository = QrRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<QrUiState>(QrUiState.Idle)
    val state: StateFlow<QrUiState> = _state

    /** O leitor de QR foi aberto. */
    fun onScanStarted() {
        _state.value = QrUiState.Scanning
    }

    /** O usuário fechou o leitor sem escanear nada. */
    fun onScanCancelled() {
        _state.value = QrUiState.Idle
    }

    /** Falha antes do envio (biometria cancelada, leitor indisponível...). */
    fun onFailure(message: String) {
        _state.value = QrUiState.Failed(message)
    }

    /** Um QR foi lido: valida o conteúdo, assina e envia ao backend. */
    fun onQrScanned(rawValue: String) {
        val payload = QrPayload.parse(rawValue)
        if (payload == null) {
            _state.value = QrUiState.Failed("QR code inválido. Escaneie o QR exibido no totem.")
            return
        }

        _state.value = QrUiState.Sending
        viewModelScope.launch {
            val session = sessionManager.getSession()
            if (session == null) {
                _state.value = QrUiState.Failed("Sessão não encontrada. Saia e entre de novo.")
                return@launch
            }

            val result = repository.register(session.token, session.userId, payload)
            _state.value = when (result) {
                is QrResult.Success -> QrUiState.Success
                is QrResult.Failure -> QrUiState.Failed(result.message)
            }
        }
    }

    fun reset() {
        _state.value = QrUiState.Idle
    }
}

/** Cria o QrViewModel (ele precisa do SessionManager, que precisa de Context). */
class QrViewModelFactory(context: Context) : ViewModelProvider.Factory {

    private val sessionManager = SessionManager(context.applicationContext)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return QrViewModel(sessionManager) as T
    }
}
