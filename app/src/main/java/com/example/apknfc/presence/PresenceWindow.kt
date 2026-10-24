package com.example.apknfc.presence

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Janela de NFC": só depois da biometria o app responde ao totem, e só por
 * [WINDOW_MS]. A janela é de uso único: depois de assinar um desafio, fecha.
 *
 * Fica em memória, no processo do app. Se o processo morrer, a janela some
 * e o serviço HCE passa a recusar (falha segura): o usuário confirma de novo.
 */
object PresenceWindow {

    const val WINDOW_MS = 30_000L

    sealed class State {
        object Idle : State()
        data class Armed(val untilElapsedMs: Long) : State()
        object Sent : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    @Volatile
    private var armedUserId: String? = null

    /** Abre a janela (chamar logo após a biometria ser confirmada). */
    fun arm(userId: String) {
        armedUserId = userId
        _state.value = State.Armed(SystemClock.elapsedRealtime() + WINDOW_MS)
    }

    /** userId se a janela estiver aberta e dentro do prazo; senão null. */
    fun currentUserIdIfArmed(): String? {
        val s = _state.value
        return if (s is State.Armed && SystemClock.elapsedRealtime() < s.untilElapsedMs) {
            armedUserId
        } else {
            null
        }
    }

    fun markSent() {
        armedUserId = null
        _state.value = State.Sent
    }

    fun markFailed(message: String) {
        armedUserId = null
        _state.value = State.Failed(message)
    }

    /** Chamado pela UI quando o tempo acaba sem nenhum toque no totem. */
    fun expire() {
        val s = _state.value
        if (s is State.Armed && SystemClock.elapsedRealtime() >= s.untilElapsedMs) {
            markFailed("O tempo para aproximar o celular acabou. Confirme a biometria de novo.")
        }
    }

    fun reset() {
        armedUserId = null
        _state.value = State.Idle
    }
}
