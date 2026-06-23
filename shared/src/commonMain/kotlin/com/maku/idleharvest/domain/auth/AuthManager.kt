package com.maku.idleharvest.domain.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthManager(private val connector: AuthConnector) {
    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun signIn() {
        _state.value = AuthState.SigningIn
        _state.value = try {
            connector.signIn()
        } catch (e: Exception) {
            AuthState.Failed(e.message ?: "Sign-in failed")
        }
    }

    suspend fun signOut() {
        try {
            connector.signOut()
        } finally {
            _state.value = AuthState.SignedOut
        }
    }
}
