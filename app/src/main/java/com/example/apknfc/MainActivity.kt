package com.example.apknfc

import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.apknfc.auth.AuthUiState
import com.example.apknfc.auth.AuthViewModel
import com.example.apknfc.auth.AuthViewModelFactory
import com.example.apknfc.auth.DeviceIdProvider
import com.example.apknfc.auth.LoginScreen
import com.example.apknfc.auth.PendingApprovalScreen
import com.example.apknfc.presence.BiometricHelper
import com.example.apknfc.presence.KeystoreManager
import com.example.apknfc.presence.PresenceSection
import com.example.apknfc.presence.PresenceWindow
import com.example.apknfc.qr.QrConfig
import com.example.apknfc.qr.QrScanner
import com.example.apknfc.qr.QrSection
import com.example.apknfc.qr.QrUiState
import com.example.apknfc.qr.QrViewModel
import com.example.apknfc.qr.QrViewModelFactory

// FragmentActivity (e não ComponentActivity) porque o BiometricPrompt exige.
class MainActivity : FragmentActivity() {

    private val authViewModel: AuthViewModel by viewModels { AuthViewModelFactory(this) }
    private val qrViewModel: QrViewModel by viewModels { QrViewModelFactory(this) }

    // Reavaliados em onResume: o usuário pode ligar o NFC ou cadastrar uma
    // biometria nas configurações e voltar para o app.
    private var nfcAtivado by mutableStateOf(false)
    private var biometricoDisponivel by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = DeviceIdProvider.getOrCreate(this)

        // O aparelho TEM o hardware de NFC? (NFC desligado nas configurações
        // conta como "tem": nesse caso o app só avisa para ligar.)
        val nfcDisponivel = NfcAdapter.getDefaultAdapter(this) != null

        // Sem NFC (ou modo de teste): usa o QR code do totem no lugar do NFC.
        val modoQr = QrConfig.FORCE_QR_FOR_TESTING || !nfcDisponivel

        // Cria a chave do Keystore (se ainda não existir) e envia a chave pública
        // no login, para o backend aprová-la junto com o vínculo do dispositivo.
        val fazerLogin: (String, String) -> Unit = { user, pass ->
            val publicKey = runCatching { KeystoreManager.getOrCreatePublicKeyBase64() }.getOrNull()
            if (publicKey == null) {
                authViewModel.showError(
                    "Não foi possível criar a chave de segurança do aparelho. " +
                            "Verifique se há bloqueio de tela e uma biometria cadastrada."
                )
            } else {
                authViewModel.login(user, pass, deviceId, publicKey)
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val uiState by authViewModel.state.collectAsState()
                    val qrState by qrViewModel.state.collectAsState()

                    when (val state = uiState) {
                        is AuthUiState.CheckingSession -> TelaCarregando()

                        is AuthUiState.LoggedOut -> LoginScreen(
                            isLoading = false,
                            errorMessage = null,
                            onLoginClick = fazerLogin
                        )

                        is AuthUiState.Loading -> LoginScreen(
                            isLoading = true,
                            errorMessage = null,
                            onLoginClick = { _, _ -> }
                        )

                        is AuthUiState.Error -> LoginScreen(
                            isLoading = false,
                            errorMessage = state.message,
                            onLoginClick = fazerLogin
                        )

                        is AuthUiState.PendingApproval -> PendingApprovalScreen(code = state.code)

                        is AuthUiState.LoggedIn -> TelaStatus(
                            modoQr = modoQr,
                            nfcDisponivel = nfcDisponivel,
                            nfcAtivado = nfcAtivado,
                            userId = state.userId,
                            biometricoDisponivel = biometricoDisponivel,
                            qrState = qrState,
                            onRegistrarNfcClick = { registrarPresencaPorNfc(state.userId) },
                            onEscanearQrClick = { registrarPresencaPorQr() },
                            onSairClick = {
                                PresenceWindow.reset()
                                qrViewModel.reset()
                                authViewModel.logout()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAtivado = NfcAdapter.getDefaultAdapter(this)?.isEnabled ?: false
        biometricoDisponivel = BiometricHelper.isAvailable(this)
    }

    /**
     * Fluxo NFC: 1) pede a biometria; 2) se confirmada, abre a janela de NFC.
     * O desafio do totem só chega no toque e é assinado pelo PresencaHceService.
     */
    private fun registrarPresencaPorNfc(userId: String) {
        BiometricHelper.authenticate(
            activity = this,
            onSuccess = { PresenceWindow.arm(userId) },
            onFailure = { mensagem -> PresenceWindow.markFailed(mensagem) }
        )
    }

    /**
     * Fluxo QR (celular sem NFC):
     *  1) pede a biometria;
     *  2) se confirmada, abre o leitor de QR do totem;
     *  3) o QrViewModel assina o desafio do QR e envia ao backend.
     * A chave do Keystore fica liberada por pouco tempo depois da biometria,
     * então o escaneamento precisa ser feito logo em seguida.
     */
    private fun registrarPresencaPorQr() {
        BiometricHelper.authenticate(
            activity = this,
            onSuccess = { abrirLeitorDeQr() },
            onFailure = { mensagem -> qrViewModel.onFailure(mensagem) }
        )
    }

    private fun abrirLeitorDeQr() {
        qrViewModel.onScanStarted()
        QrScanner.scan(
            context = this,
            onResult = { texto -> qrViewModel.onQrScanned(texto) },
            onCancel = { qrViewModel.onScanCancelled() },
            onError = { mensagem -> qrViewModel.onFailure(mensagem) }
        )
    }
}

@Composable
fun TelaCarregando() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun TelaStatus(
    modoQr: Boolean,
    nfcDisponivel: Boolean,
    nfcAtivado: Boolean,
    userId: String,
    biometricoDisponivel: Boolean,
    qrState: QrUiState,
    onRegistrarNfcClick: () -> Unit,
    onEscanearQrClick: () -> Unit,
    onSairClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "App de Presença NFC", style = MaterialTheme.typography.headlineSmall)

        if (modoQr) {
            Text(
                text = "Modo QR code: escaneie o código exibido no totem.",
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            Text(
                text = "NFC do aparelho: " + if (nfcDisponivel) "disponível" else "não disponível",
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "NFC ativado: " + if (nfcAtivado) "sim" else "não (ative nas configurações)",
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Text(
            text = "Conta:",
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(text = userId, style = MaterialTheme.typography.headlineMedium)

        if (modoQr) {
            QrSection(
                state = qrState,
                biometricAvailable = biometricoDisponivel,
                onScanClick = onEscanearQrClick,
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            PresenceSection(
                biometricAvailable = biometricoDisponivel,
                onRegisterClick = onRegistrarNfcClick,
                modifier = Modifier.padding(top = 24.dp)
            )
        }

        TextButton(
            onClick = onSairClick,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("Sair")
        }
    }
}