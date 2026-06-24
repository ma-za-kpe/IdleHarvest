package com.maku.idleharvest.domain.auth

actual fun createAuthConnector(): AuthConnector = object : AuthConnector {
    override suspend fun signIn(): AuthState.SignedIn = throw UnsupportedOperationException("Use Firebase Auth UI on iOS")

    override suspend fun signOut() = Unit
}
