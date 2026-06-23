package com.maku.idleharvest.domain.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ── Promise bridge ────────────────────────────────────────────────────────────

@JsFun(
    """
(promise, onSuccess, onError) => {
    promise.then(
        function(v) { onSuccess(v == null ? null : v); },
        function(e) { onError(e ? (e.message || String(e)) : 'Unknown error'); }
    );
}
""",
)
private external fun promiseThen(
    promise: JsAny,
    onSuccess: (JsAny?) -> Unit,
    onError: (String) -> Unit,
)

private suspend fun awaitJs(promise: JsAny): JsAny? = suspendCancellableCoroutine { cont ->
    promiseThen(
        promise = promise,
        onSuccess = { value -> cont.resume(value) },
        onError = { msg -> cont.resumeWithException(Exception(msg)) },
    )
}

// ── Firebase Auth JS SDK external declarations ────────────────────────────────

private external interface FirebaseUser : JsAny {
    val uid: String
    val displayName: String?
    val email: String?
    val photoURL: String?
}

@JsFun("() => window._firebaseApp || null")
private external fun getFirebaseApp(): JsAny?

@JsFun("(app) => window.firebase_getAuth ? window.firebase_getAuth(app) : null")
private external fun getAuth(app: JsAny?): JsAny?

@JsFun(
    """
(auth) => {
    if (!auth || !window.firebase_signInWithPopup || !window.firebase_GoogleAuthProvider) {
        return Promise.reject(new Error('Firebase Auth not ready'));
    }
    var provider = new window.firebase_GoogleAuthProvider();
    return window.firebase_signInWithPopup(auth, provider);
}
""",
)
private external fun jsSignInWithPopup(auth: JsAny?): JsAny

@JsFun("(auth) => auth && window.firebase_signOut ? window.firebase_signOut(auth) : Promise.resolve()")
private external fun jsSignOut(auth: JsAny?): JsAny

@JsFun("(cred) => cred && cred.user ? cred.user : null")
private external fun getUserFromCredential(cred: JsAny?): FirebaseUser?

@JsFun("(user) => user ? user.uid : ''")
private external fun getUid(user: FirebaseUser): String

@JsFun("(user) => user ? (user.displayName || '') : ''")
private external fun getDisplayName(user: FirebaseUser): String

@JsFun("(user) => user ? (user.email || '') : ''")
private external fun getEmail(user: FirebaseUser): String

@JsFun("(user) => user ? (user.photoURL || null) : null")
private external fun getPhotoUrl(user: FirebaseUser): String?

// ── Session restoration (page reload) ────────────────────────────────────────

@JsFun("() => window._getPendingAuthUser ? window._getPendingAuthUser() : null")
private external fun getPendingAuthUser(): FirebaseUser?

// ── Firestore helpers ─────────────────────────────────────────────────────────

@JsFun(
    """
(userId, displayName, email, photoUrl) => {
    if (!window.firebase_saveUserDoc) return Promise.resolve();
    return window.firebase_saveUserDoc(userId, displayName, email, photoUrl);
}
""",
)
private external fun jsSaveUserDoc(userId: String, displayName: String, email: String, photoUrl: String): JsAny

// ── Actual implementation ─────────────────────────────────────────────────────

actual fun createAuthConnector(): AuthConnector = object : AuthConnector {

    override suspend fun getExistingSession(): AuthState.SignedIn? {
        val user = getPendingAuthUser() ?: return null
        return AuthState.SignedIn(
            userId = getUid(user),
            displayName = getDisplayName(user).ifEmpty { "User" },
            email = getEmail(user),
            photoUrl = getPhotoUrl(user),
        )
    }

    override suspend fun signIn(): AuthState.SignedIn {
        val app = getFirebaseApp()
        val auth = getAuth(app)
        val cred = awaitJs(jsSignInWithPopup(auth))
        val user = getUserFromCredential(cred)
            ?: throw IllegalStateException("No user returned from Google Sign-In")
        val state = AuthState.SignedIn(
            userId = getUid(user),
            displayName = getDisplayName(user).ifEmpty { "User" },
            email = getEmail(user),
            photoUrl = getPhotoUrl(user),
        )
        // Persist user doc in Firestore (merge so existing data is kept)
        awaitJs(jsSaveUserDoc(state.userId, state.displayName, state.email, state.photoUrl ?: ""))
        return state
    }

    override suspend fun signOut() {
        val app = getFirebaseApp()
        val auth = getAuth(app)
        awaitJs(jsSignOut(auth))
    }
}
