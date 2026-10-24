package com.example.apknfc.presence

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Botão "Registrar presença" + estado da janela de NFC
 * (aguardando toque, enviado, erro).
 */
@Composable
fun PresenceSection(
    biometricAvailable: Boolean,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by PresenceWindow.state.collectAsState()
    var secondsLeft by remember { mutableStateOf(0) }

    // Contagem regressiva enquanto a janela está aberta; ao zerar, fecha a janela.
    LaunchedEffect(state) {
        val current = state
        if (current is PresenceWindow.State.Armed) {
            while (true) {
                val remainingMs = current.untilElapsedMs - SystemClock.elapsedRealtime()
                if (remainingMs <= 0) {
                    PresenceWindow.expire()
                    break
                }
                secondsLeft = ((remainingMs + 999) / 1000).toInt()
                delay(250)
            }
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (val s = state) {
            is PresenceWindow.State.Armed -> {
                Text(
                    text = "Aproxime o celular do totem agora",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${secondsLeft}s restantes",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            is PresenceWindow.State.Sent -> Text(
                text = "Sinal enviado ao totem.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            is PresenceWindow.State.Failed -> Text(
                text = s.message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )

            PresenceWindow.State.Idle -> Unit
        }

        if (!biometricAvailable) {
            Text(
                text = "Cadastre uma biometria forte (digital ou rosto) nas configurações do aparelho para registrar presença.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        val jaTentou = state is PresenceWindow.State.Sent || state is PresenceWindow.State.Failed
        Button(
            enabled = biometricAvailable && state !is PresenceWindow.State.Armed,
            onClick = onRegisterClick,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(if (jaTentou) "Registrar novamente" else "Registrar presença")
        }
    }
}
