package com.scrub.app.auth

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Which request is running, so the UI can show the spinner in the button that was pressed. */
enum class AuthAction { Google, SignIn, Register, Reset, SignOut, Delete, Reauth }

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AuthRepository(app)
    private val prefs = app.getSharedPreferences("account", Context.MODE_PRIVATE)

    val currentUser: StateFlow<AuthUser?> = repo.currentUser
    val restoring: StateFlow<Boolean> = repo.restoring

    /** The running request, or null. While set, every auth button is disabled. */
    var pending by mutableStateOf<AuthAction?>(null)
        private set

    /** Last failure from Firebase, for the current form. */
    var error by mutableStateOf<AuthError?>(null)
        private set

    /** Which request produced [error], so it can be shown next to the right button. */
    var errorFrom by mutableStateOf<AuthAction?>(null)
        private set

    /** Set once a reset link was sent; holds the address it went to. */
    var resetSentTo by mutableStateOf<String?>(null)
        private set

    /** Deleting the account needs a fresh sign-in first. */
    var needsReauth by mutableStateOf(false)
        private set

    private var verifyNoteDismissedFor by mutableStateOf(prefs.getString(KEY_VERIFY_DISMISSED, null))

    init {
        viewModelScope.launch { repo.refresh() }
    }

    fun showVerifyNote(user: AuthUser?): Boolean =
        user != null && user.method == SignInMethod.Email && !user.emailVerified &&
            verifyNoteDismissedFor != user.uid

    fun dismissVerifyNote(user: AuthUser) {
        verifyNoteDismissedFor = user.uid
        prefs.edit { putString(KEY_VERIFY_DISMISSED, user.uid) }
    }

    /** Clears errors and the "reset link sent" state, e.g. when switching screens. */
    fun clearMessages() {
        error = null
        resetSentTo = null
    }

    fun signInWithEmail(email: String, password: String) =
        run(AuthAction.SignIn) { repo.signInWithEmail(email, password) }

    fun register(name: String, email: String, password: String) =
        run(AuthAction.Register) { repo.registerWithEmail(name, email, password) }

    fun signInWithGoogle(activity: Activity) =
        run(AuthAction.Google) { repo.signInWithGoogle(activity) }

    fun sendPasswordReset(email: String) = run(AuthAction.Reset) {
        repo.sendPasswordReset(email).onSuccess { resetSentTo = email.trim() }
    }

    fun signOut() = run(AuthAction.SignOut) { repo.signOut() }

    fun deleteAccount() = run(AuthAction.Delete) {
        repo.deleteAccount().onFailure {
            if ((it as? AuthError)?.kind == AuthError.Kind.RecentLoginRequired) needsReauth = true
        }
    }

    /** Re-enter the password, then retry the delete. */
    fun reauthWithPassword(password: String) = run(AuthAction.Reauth) {
        repo.reauthenticateWithPassword(password).onSuccess { retryDelete() }
    }

    /** Pick the Google account again, then retry the delete. */
    fun reauthWithGoogle(activity: Activity) = run(AuthAction.Reauth) {
        repo.reauthenticateWithGoogle(activity).onSuccess { retryDelete() }
    }

    fun cancelReauth() {
        needsReauth = false
        error = null
    }

    private suspend fun retryDelete() {
        needsReauth = false
        repo.deleteAccount().onFailure {
            if ((it as? AuthError)?.kind == AuthError.Kind.RecentLoginRequired) needsReauth = true
            throw it
        }
    }

    private fun run(action: AuthAction, block: suspend () -> Result<Unit>) {
        if (pending != null) return
        pending = action
        error = null
        viewModelScope.launch {
            val result = try {
                block()
            } catch (e: AuthError) {
                Result.failure(e)
            }
            result.exceptionOrNull()?.let { e ->
                val authError = e as? AuthError ?: AuthError(AuthRepository.GENERIC, cause = e)
                // Closing the Google picker isn't an error: stay on the screen quietly.
                if (authError.kind != AuthError.Kind.Cancelled &&
                    authError.kind != AuthError.Kind.RecentLoginRequired
                ) {
                    error = authError
                    errorFrom = action
                }
            }
            pending = null
        }
    }

    override fun onCleared() = repo.close()

    private companion object {
        const val KEY_VERIFY_DISMISSED = "verify_note_dismissed_uid"
    }
}
