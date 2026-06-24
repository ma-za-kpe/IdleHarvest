package com.maku.idleharvest.domain.auth

interface AuthConnector {
    suspend fun signIn(): AuthState.SignedIn
    suspend fun signOut()
    suspend fun getExistingSession(): AuthState.SignedIn? = null
}

expect fun createAuthConnector(): AuthConnector
