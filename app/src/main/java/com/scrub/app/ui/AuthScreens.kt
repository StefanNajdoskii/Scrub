package com.scrub.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.scrub.app.R
import com.scrub.app.auth.AuthAction
import com.scrub.app.auth.AuthError
import com.scrub.app.auth.AuthRepository
import com.scrub.app.auth.AuthValidation
import com.scrub.app.auth.AuthViewModel

private enum class AuthRoute { SignIn, CreateAccount, ForgotPassword }

/** Signed-out part of the app: sign in, create account, reset password. */
@Composable
fun AuthFlow(vm: AuthViewModel) {
    val activity = LocalContext.current.findActivity()
    var route by rememberSaveable { mutableStateOf(AuthRoute.SignIn) }
    // Shared between the screens so an address typed once is prefilled everywhere.
    var email by rememberSaveable { mutableStateOf("") }

    fun go(to: AuthRoute) {
        vm.clearMessages()
        route = to
    }

    BackHandler(enabled = route != AuthRoute.SignIn) { go(AuthRoute.SignIn) }

    Crossfade(targetState = route, label = "auth") { r ->
        when (r) {
            AuthRoute.SignIn -> SignInScreen(
                email = email,
                onEmailChange = { email = it },
                pending = vm.pending,
                error = vm.error,
                errorFrom = vm.errorFrom,
                onGoogle = { activity?.let(vm::signInWithGoogle) },
                onSignIn = { password -> vm.signInWithEmail(email, password) },
                onForgotPassword = { go(AuthRoute.ForgotPassword) },
                onCreateAccount = { go(AuthRoute.CreateAccount) },
            )
            AuthRoute.CreateAccount -> CreateAccountScreen(
                email = email,
                onEmailChange = { email = it },
                pending = vm.pending,
                error = vm.error,
                onCreate = { name, password -> vm.register(name, email, password) },
                onSignInInstead = { go(AuthRoute.SignIn) },
                onBack = { go(AuthRoute.SignIn) },
            )
            AuthRoute.ForgotPassword -> ForgotPasswordScreen(
                email = email,
                onEmailChange = { email = it },
                pending = vm.pending,
                error = vm.error,
                sentTo = vm.resetSentTo,
                onSend = { vm.sendPasswordReset(email) },
                onBack = { go(AuthRoute.SignIn) },
            )
        }
    }
}

/** Shown while Firebase restores the saved session, so the sign-in screen never flashes. */
@Composable
fun StartupScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Wordmark(animate = false)
    }
}

/**
 * Scrollable form column that keeps [bottom] pinned to the bottom of the screen when there's
 * room, and scrolls everything above the keyboard when there isn't.
 */
@Composable
private fun FormColumn(
    bottom: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column { content() }
            bottom()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Sign in
// ---------------------------------------------------------------------------------------------

@Composable
fun SignInScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    pending: AuthAction?,
    error: AuthError?,
    errorFrom: AuthAction?,
    onGoogle: () -> Unit,
    onSignIn: (password: String) -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val c = ScrubTheme.colors
    val focusManager = LocalFocusManager.current
    var password by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf(false) }
    val idle = pending == null

    val emailError = if (checked) AuthValidation.email(email) else null
    val passwordError = if (checked) AuthValidation.existingPassword(password) else null
    val googleError = error?.message?.takeIf { errorFrom == AuthAction.Google }
    val formError = error?.message?.takeIf { errorFrom == AuthAction.SignIn }

    fun submit() {
        checked = true
        if (AuthValidation.email(email) == null && AuthValidation.existingPassword(password) == null) {
            focusManager.clearFocus()
            onSignIn(password)
        }
    }

    FormColumn(
        bottom = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Gutter, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("New here?", style = ScrubType.Body, color = c.pencil)
                QuietButton("Create an account", onClick = onCreateAccount, enabled = idle)
            }
        },
    ) {
        Column(Modifier.padding(horizontal = Gutter)) {
            Spacer(Modifier.height(40.dp))
            Wordmark()
            Spacer(Modifier.height(8.dp))
            Text("Clear one person out of your photos.", style = ScrubType.Body, color = c.pencil)

            Spacer(Modifier.height(40.dp))
            SecondaryButton(
                text = "Continue with Google",
                onClick = onGoogle,
                painter = painterResource(R.drawable.ic_google_g),
                enabled = idle,
                loading = pending == AuthAction.Google,
            )
            if (googleError != null) FieldError(googleError, Modifier.padding(top = 8.dp))

            LabeledDivider("or", Modifier.padding(vertical = 24.dp))

            ScrubTextField(
                value = email,
                onValueChange = onEmailChange,
                label = "Email",
                kind = FieldKind.Email,
                error = emailError,
                enabled = idle,
            )
            Spacer(Modifier.height(16.dp))
            ScrubTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                kind = FieldKind.Password,
                error = passwordError,
                imeAction = ImeAction.Done,
                onDone = ::submit,
                enabled = idle,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                QuietButton(
                    "Forgot password?",
                    onClick = onForgotPassword,
                    enabled = idle,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (formError != null) FieldError(formError, Modifier.padding(bottom = 12.dp))
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = "Sign in",
                onClick = ::submit,
                enabled = idle,
                loading = pending == AuthAction.SignIn,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Create account
// ---------------------------------------------------------------------------------------------

@Composable
fun CreateAccountScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    pending: AuthAction?,
    error: AuthError?,
    onCreate: (name: String, password: String) -> Unit,
    onSignInInstead: () -> Unit,
    onBack: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf(false) }
    val idle = pending == null

    // Firebase errors that belong to a field go under it; the rest go above the button.
    val serverEmail = error?.message?.takeIf { error.field == AuthError.Field.Email }
    val serverPassword = error?.message?.takeIf { error.field == AuthError.Field.Password }
    val formError = error?.message?.takeIf { error.field == null }

    val nameError = if (checked) AuthValidation.name(name) else null
    val emailError = (if (checked) AuthValidation.email(email) else null) ?: serverEmail
    val passwordError = (if (checked) AuthValidation.newPassword(password) else null) ?: serverPassword
    val confirmError = if (checked) AuthValidation.confirmPassword(password, confirm) else null
    val longEnough = password.length >= AuthValidation.MIN_PASSWORD
    val toGo = AuthValidation.MIN_PASSWORD - password.length

    fun submit() {
        checked = true
        val ok = AuthValidation.name(name) == null &&
            AuthValidation.email(email) == null &&
            AuthValidation.newPassword(password) == null &&
            AuthValidation.confirmPassword(password, confirm) == null
        if (ok) {
            focusManager.clearFocus()
            onCreate(name, password)
        }
    }

    FormColumn {
        TopBar("Create an account", onBack = onBack)
        Column(Modifier.padding(horizontal = Gutter)) {
            Spacer(Modifier.height(8.dp))
            ScrubTextField(name, { name = it }, "Name", FieldKind.Name, error = nameError, enabled = idle)
            Spacer(Modifier.height(16.dp))
            ScrubTextField(email, onEmailChange, "Email", FieldKind.Email, error = emailError, enabled = idle)
            if (error?.kind == AuthError.Kind.EmailInUse) {
                QuietButton("Sign in instead", onClick = onSignInInstead, enabled = idle)
            }
            Spacer(Modifier.height(16.dp))
            ScrubTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                kind = FieldKind.NewPassword,
                // Live hint: counts down, then ticks. Turns into an error only after submitting.
                error = passwordError,
                hint = if (longEnough || password.isEmpty()) "At least 8 characters"
                else "At least 8 characters · $toGo more",
                hintMet = longEnough,
                enabled = idle,
            )
            Spacer(Modifier.height(16.dp))
            ScrubTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = "Confirm password",
                kind = FieldKind.NewPassword,
                error = confirmError,
                imeAction = ImeAction.Done,
                onDone = ::submit,
                enabled = idle,
            )
            Spacer(Modifier.height(24.dp))
            if (formError != null) FieldError(formError, Modifier.padding(bottom = 12.dp))
            PrimaryButton(
                text = "Create account",
                onClick = ::submit,
                enabled = idle,
                loading = pending == AuthAction.Register,
            )
            Spacer(Modifier.height(8.dp))
            QuietButton(
                "I already have an account",
                onClick = onSignInInstead,
                enabled = idle,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Forgot password
// ---------------------------------------------------------------------------------------------

@Composable
fun ForgotPasswordScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    pending: AuthAction?,
    error: AuthError?,
    sentTo: String?,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    val c = ScrubTheme.colors
    val focusManager = LocalFocusManager.current
    var checked by rememberSaveable { mutableStateOf(false) }
    val idle = pending == null
    val emailError = (if (checked) AuthValidation.email(email) else null)
        ?: error?.message?.takeIf { error.field == AuthError.Field.Email }
    val formError = error?.message?.takeIf { error.field == null }

    fun submit() {
        checked = true
        if (AuthValidation.email(email) == null) {
            focusManager.clearFocus()
            onSend()
        }
    }

    FormColumn {
        TopBar("Reset your password", onBack = onBack)
        Column(Modifier.padding(horizontal = Gutter)) {
            if (sentTo != null) {
                Spacer(Modifier.height(48.dp))
                Hero(
                    icon = Icons.Outlined.MarkEmailRead,
                    title = "Check your email",
                    body = "Reset link sent to $sentTo. Check your inbox and spam folder.",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(40.dp))
                PrimaryButton("Back to sign in", onClick = onBack)
            } else {
                Text(
                    "Enter the email you signed up with. We'll send you a link to choose a new password.",
                    style = ScrubType.Body,
                    color = c.pencil,
                )
                Spacer(Modifier.height(24.dp))
                ScrubTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = "Email",
                    kind = FieldKind.Email,
                    error = emailError,
                    imeAction = ImeAction.Done,
                    onDone = ::submit,
                    enabled = idle,
                )
                Spacer(Modifier.height(24.dp))
                if (formError != null) FieldError(formError, Modifier.padding(bottom = 12.dp))
                PrimaryButton(
                    text = "Send reset link",
                    onClick = ::submit,
                    enabled = idle,
                    loading = pending == AuthAction.Reset,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@ScreenPreviews
@Composable
private fun SignInPreview() = PreviewFrame {
    SignInScreen(
        email = "", onEmailChange = {}, pending = null, error = null, errorFrom = null,
        onGoogle = {}, onSignIn = {}, onForgotPassword = {}, onCreateAccount = {},
    )
}

@ScreenPreviews
@Composable
private fun SignInErrorPreview() = PreviewFrame {
    SignInScreen(
        email = "ada@example.com", onEmailChange = {}, pending = null,
        error = AuthError(AuthRepository.WRONG_CREDENTIALS), errorFrom = AuthAction.SignIn,
        onGoogle = {}, onSignIn = {}, onForgotPassword = {}, onCreateAccount = {},
    )
}

@ScreenPreviews
@Composable
private fun SignInLoadingPreview() = PreviewFrame {
    SignInScreen(
        email = "ada@example.com", onEmailChange = {}, pending = AuthAction.Google,
        error = null, errorFrom = null,
        onGoogle = {}, onSignIn = {}, onForgotPassword = {}, onCreateAccount = {},
    )
}

@ScreenPreviews
@Composable
private fun CreateAccountPreview() = PreviewFrame {
    CreateAccountScreen(
        email = "ada@example.com", onEmailChange = {}, pending = null,
        error = AuthError(AuthRepository.EMAIL_IN_USE, AuthError.Kind.EmailInUse, AuthError.Field.Email),
        onCreate = { _, _ -> }, onSignInInstead = {}, onBack = {},
    )
}

@ScreenPreviews
@Composable
private fun ForgotPasswordPreview() = PreviewFrame {
    ForgotPasswordScreen(
        email = "ada@example.com", onEmailChange = {}, pending = null, error = null,
        sentTo = null, onSend = {}, onBack = {},
    )
}

@ScreenPreviews
@Composable
private fun ForgotPasswordSentPreview() = PreviewFrame {
    ForgotPasswordScreen(
        email = "ada@example.com", onEmailChange = {}, pending = null, error = null,
        sentTo = "ada@example.com", onSend = {}, onBack = {},
    )
}

@ScreenPreviews
@Composable
private fun StartupPreview() = PreviewFrame { StartupScreen() }
