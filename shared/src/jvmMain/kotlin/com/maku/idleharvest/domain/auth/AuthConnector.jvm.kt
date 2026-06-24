package com.maku.idleharvest.domain.auth

actual fun createAuthConnector(): AuthConnector = object : AuthConnector {
    override suspend fun signIn(): AuthState.SignedIn = throw UnsupportedOperationException("Auth is not available on the JVM backend")

    override suspend fun signOut() = Unit

    override suspend fun getExistingSession(): AuthState.SignedIn? = null
}
