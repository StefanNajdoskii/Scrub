package com.scrub.app.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.scrub.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

enum class SignInMethod { Email, Google }

/** The signed-in account, as the UI sees it. */
data class AuthUser(
    val uid: String,
    val name: String?,
    val email: String?,
    val photoUrl: String?,
    val method: SignInMethod,
    val emailVerified: Boolean,
) {
    /** Name to show: the display name, or the part of the email before the @. */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "You"

    val initial: String
        get() = displayName.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
}

/** An auth failure with a message that can be shown to the user as-is. */
class AuthError(
    message: String,
    val kind: Kind = Kind.Other,
    /** The form field this error belongs under, if any. */
    val field: Field? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind { Other, Cancelled, RecentLoginRequired, EmailInUse }
    enum class Field { Email, Password }
}

/**
 * Thin wrapper around Firebase Authentication. This is the ONLY code in the app that talks to
 * the network. It sends account details (name, email, password, Google ID token) and nothing
 * else: never photos, face fingerprints or the vault. See CLAUDE.md.
 */
class AuthRepository(context: Context) {

    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(appContext)

    private val _currentUser = MutableStateFlow(auth.currentUser?.toAuthUser())
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    /** True until Firebase has finished restoring the saved session at startup. */
    private val _restoring = MutableStateFlow(true)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    private val listener = FirebaseAuth.AuthStateListener {
        publish()
        _restoring.value = false
    }

    init {
        auth.addAuthStateListener(listener)
    }

    fun close() = auth.removeAuthStateListener(listener)

    private fun publish() {
        _currentUser.value = auth.currentUser?.toAuthUser()
    }

    /** Re-reads the account from Firebase (e.g. to pick up a verified email). Fails silently offline. */
    suspend fun refresh() {
        attempt(Op.Other) { auth.currentUser?.reload()?.await() }
        publish()
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = attempt(Op.SignIn) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        publish()
    }

    suspend fun registerWithEmail(name: String, email: String, password: String): Result<Unit> =
        attempt(Op.Register) {
            val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
                ?: throw AuthError(GENERIC)
            // The account exists now. A failure in these two steps must not look like a failed sign-up.
            attempt(Op.Other) {
                user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
            }
            attempt(Op.Other) { user.sendEmailVerification().await() }
            publish()
        }

    suspend fun sendPasswordReset(email: String): Result<Unit> = attempt(Op.Reset) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun signInWithGoogle(activity: Activity): Result<Unit> = attempt(Op.Google) {
        auth.signInWithCredential(googleCredential(activity)).await()
        publish()
    }

    /** Confirms the password of the signed-in email account (needed before deleting it). */
    suspend fun reauthenticateWithPassword(password: String): Result<Unit> = attempt(Op.Reauth) {
        val user = auth.currentUser ?: throw AuthError(SIGNED_OUT)
        val email = user.email ?: throw AuthError(GENERIC)
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
    }

    /** Confirms the Google account of the signed-in user (needed before deleting it). */
    suspend fun reauthenticateWithGoogle(activity: Activity): Result<Unit> = attempt(Op.Google) {
        val user = auth.currentUser ?: throw AuthError(SIGNED_OUT)
        user.reauthenticate(googleCredential(activity)).await()
    }

    suspend fun signOut(): Result<Unit> = attempt(Op.Other) {
        auth.signOut()
        clearCredentials()
        publish()
    }

    /**
     * Deletes the Firebase account. Fails with [AuthError.Kind.RecentLoginRequired] if the user
     * must sign in again first. Never touches photos, faces.bin or the vault.
     */
    suspend fun deleteAccount(): Result<Unit> = attempt(Op.Delete) {
        val user = auth.currentUser ?: throw AuthError(SIGNED_OUT)
        user.delete().await()
        clearCredentials()
        publish()
    }

    // ---------------------------------------------------------------------------------------------

    private suspend fun googleCredential(activity: Activity): AuthCredential {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(appContext.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = credentialManager.getCredential(activity, request).credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) throw AuthError(GOOGLE_FAILED)
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return GoogleAuthProvider.getCredential(idToken, null)
    }

    private suspend fun clearCredentials() {
        attempt(Op.Other) { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private enum class Op { SignIn, Register, Reset, Google, Reauth, Delete, Other }

    /** Runs [block] and turns any failure into an [AuthError] with a plain message. */
    private suspend fun <T> attempt(op: Op, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(friendly(e, op))
    }

    private fun friendly(e: Exception, op: Op): AuthError = when (e) {
        is AuthError -> e

        // Credential Manager (Google account picker)
        is GetCredentialCancellationException -> AuthError("", AuthError.Kind.Cancelled, cause = e)
        is NoCredentialException -> AuthError(
            "No Google account found on this phone. Add one in Settings, then try again.", cause = e,
        )
        is GetCredentialException, is GoogleIdTokenParsingException -> AuthError(GOOGLE_FAILED, cause = e)

        // Firebase
        is FirebaseNetworkException -> AuthError(NO_NETWORK, cause = e)
        is FirebaseTooManyRequestsException -> AuthError(
            "Too many attempts. Wait a few minutes, then try again.", cause = e,
        )
        is FirebaseAuthRecentLoginRequiredException -> AuthError(
            "For your security, sign in again to continue.", AuthError.Kind.RecentLoginRequired, cause = e,
        )
        is FirebaseAuthUserCollisionException ->
            if (op == Op.Google) AuthError(
                "This email already has a Scrub account with a password. Sign in with your email instead.",
                cause = e,
            )
            else AuthError(EMAIL_IN_USE, AuthError.Kind.EmailInUse, AuthError.Field.Email, e)
        // Must come before InvalidCredentials: it's a subclass of it.
        is FirebaseAuthWeakPasswordException -> AuthError(WEAK_PASSWORD, field = AuthError.Field.Password, cause = e)
        is FirebaseAuthInvalidUserException -> when {
            e.errorCode == "ERROR_USER_DISABLED" -> AuthError("This account has been turned off.", cause = e)
            e.errorCode == "ERROR_USER_MISMATCH" -> AuthError(
                "That's a different Google account. Pick the one you signed in with.", cause = e,
            )
            op == Op.Reauth || op == Op.Delete -> AuthError(SIGNED_OUT, cause = e)
            else -> AuthError(WRONG_CREDENTIALS, cause = e)
        }
        is FirebaseAuthInvalidCredentialsException -> when {
            e.errorCode == "ERROR_INVALID_EMAIL" ->
                AuthError("Enter a valid email address.", field = AuthError.Field.Email, cause = e)
            op == Op.Reauth -> AuthError("That password is incorrect.", field = AuthError.Field.Password, cause = e)
            op == Op.Google -> AuthError(GOOGLE_FAILED, cause = e)
            else -> AuthError(WRONG_CREDENTIALS, cause = e)
        }
        is FirebaseAuthException -> AuthError(GENERIC, cause = e)
        else -> AuthError(GENERIC, cause = e)
    }

    private fun FirebaseUser.toAuthUser(): AuthUser {
        val google = providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        return AuthUser(
            uid = uid,
            name = displayName ?: google?.displayName,
            email = email ?: google?.email,
            photoUrl = (photoUrl ?: google?.photoUrl)?.toString(),
            method = if (google != null) SignInMethod.Google else SignInMethod.Email,
            emailVerified = isEmailVerified,
        )
    }

    companion object {
        const val WRONG_CREDENTIALS = "Email or password is incorrect."
        const val EMAIL_IN_USE = "An account with this email already exists. Sign in instead?"
        const val WEAK_PASSWORD = "Use at least 8 characters."
        const val NO_NETWORK = "No internet connection. Signing in needs internet; scanning your photos doesn't."
        const val GOOGLE_FAILED = "Google sign-in didn't work. Try again."
        const val SIGNED_OUT = "You've been signed out. Sign in again."
        const val GENERIC = "Something went wrong. Try again."
    }
}
