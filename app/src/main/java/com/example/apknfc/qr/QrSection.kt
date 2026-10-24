package com.example.apknfc.qr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Parte da tela usada em celulares sem NFC: botão "Escanear QR do totem"
 * e o estado atual (escaneando, enviando, sucesso, erro).
 */
@Composable
fun QrSection(
    state: QrUiState,
    biometricAvailable: Boolean,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {

        when (state) {
            QrUiState.Idle -> Text(
                text = "Confirme a biometria e escaneie o QR code exibido no totem.",
                textAlign = TextAlign.Center
            )

            QrUiState.Scanning -> Text(
                text = "Aponte a câmera para o QR code do totem.",
                textAlign = TextAlign.Center
            )

            QrUiState.Sending -> {
                CircularProgressIndicator()
                Text(
                    text = "Registrando presença...",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            QrUiState.Success -> Text(
                text = "Presença registrada.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            is QrUiState.Failed -> Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        if (!biometricAvailable) {
            Text(
                text = "Cadastre uma biometria forte (digital ou rosto) nas configurações do aparelho para registrar presença.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        val ocupado = state is QrUiState.Scanning || state is QrUiState.Sending
        val jaTentou = state is QrUiState.Success || state is QrUiState.Failed

        Button(
            enabled = biometricAvailable && !ocupado,
            onClick = onScanClick,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(if (jaTentou) "Escanear novamente" else "Escanear QR do totem")
        }
    }
}
