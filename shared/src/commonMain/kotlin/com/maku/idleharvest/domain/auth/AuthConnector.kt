package com.maku.idleharvest.domain.auth

interface AuthConnector {
    suspend fun signIn(): AuthState.SignedIn
    suspend fun signOut()
}

expect fun createAuthConnector(): AuthConnector
