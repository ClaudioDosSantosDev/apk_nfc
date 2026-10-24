package com.example.apknfc.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AuthViewModelFactory(context: Context) : ViewModelProvider.Factory {

    private val sessionManager = SessionManager(context.applicationContext)

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AuthViewModel(sessionManager = sessionManager) as T
    }
}
