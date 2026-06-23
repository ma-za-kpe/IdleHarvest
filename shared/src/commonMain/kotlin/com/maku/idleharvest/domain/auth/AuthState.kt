package com.maku.idleharvest.domain.auth

sealed class AuthState {
    data object SignedOut : AuthState()

    data object SigningIn : AuthState()

    data class SignedIn(
        val userId: String,
        val displayName: String,
        val email: String,
        val photoUrl: String?,
    ) : AuthState()

    data class Failed(val reason: String) : AuthState()
}
