package com.example.apknfc.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session_prefs")

/**
 * Guarda a sessão (token + userId) em disco usando DataStore,
 * para o usuário não precisar logar toda vez que abrir o app.
 *
 * Isso NÃO substitui o vínculo device_id <-> usuário que o backend
 * controla — é só um "fique logado" local. Se o backend revogar o
 * dispositivo, a próxima chamada autenticada deve falhar e o app
 * precisa chamar clearSession() e voltar pro login.
 */
class SessionManager(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
    }

    val sessionFlow: Flow<Session?> = context.sessionDataStore.data.map { prefs ->
        val token = prefs[Keys.TOKEN]
        val userId = prefs[Keys.USER_ID]
        if (token != null && userId != null) Session(userId, token) else null
    }

    suspend fun getSession(): Session? = sessionFlow.first()

    suspend fun saveSession(userId: String, token: String) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.USER_ID] = userId
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(Keys.TOKEN)
            prefs.remove(Keys.USER_ID)
        }
    }
}

data class Session(val userId: String, val token: String)
