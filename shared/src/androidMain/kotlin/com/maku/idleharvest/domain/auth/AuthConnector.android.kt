package com.maku.idleharvest.domain.auth

actual fun createAuthConnector(): AuthConnector = object : AuthConnector {
    override suspend fun signIn(): AuthState.SignedIn = throw UnsupportedOperationException("Use Firebase Auth UI on Android")

    override suspend fun signOut() = Unit
}
