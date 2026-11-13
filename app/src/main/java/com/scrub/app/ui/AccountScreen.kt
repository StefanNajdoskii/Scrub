package com.scrub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.scrub.app.R
import com.scrub.app.auth.AuthAction
import com.scrub.app.auth.AuthError
import com.scrub.app.auth.AuthUser
import com.scrub.app.auth.SignInMethod



@Composable
fun AccountScreen(
    user: AuthUser,
    pending: AuthAction?,
    error: AuthError?,
    errorFrom: AuthAction?,
    needsReauth: Boolean,
    settings: AppSettingsUi,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
    onReauthWithPassword: (String) -> Unit,
    onReauthWithGoogle: () -> Unit,
    onCancelReauth: () -> Unit,
) {
    val c = ScrubTheme.colors
    val idle = pending == null
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val signOutError = errorText(error?.messageRes?.takeIf { errorFrom == AuthAction.SignOut })
    // A failed retry after signing in again also counts as a delete error.
    val deleteError = errorText(error?.messageRes?.takeIf {
        !needsReauth && (errorFrom == AuthAction.Delete || errorFrom == AuthAction.Reauth)
    })
    val name = user.displayName ?: stringResource(R.string.account_name_fallback)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopBar(stringResource(R.string.account_title), onBack = onBack)
        Column(Modifier.padding(horizontal = Gutter)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(user.initial, user.photoUrl, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = ScrubType.SectionTitle,
                        color = c.graphite,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (user.email != null) Text(user.email, style = ScrubType.Small, color = c.pencil)
                }
            }

            Spacer(Modifier.height(32.dp))
            Hairline()
            DetailRow(stringResource(R.string.account_name), name)
            Hairline()
            DetailRow(stringResource(R.string.account_email), user.email ?: stringResource(R.string.account_email_none))
            Hairline()
            DetailRow(
                stringResource(R.string.account_signed_in_with),
                when (user.method) {
                    SignInMethod.Google -> stringResource(R.string.brand_google)
                    SignInMethod.Email -> stringResource(R.string.account_method_email)
                },
            )
            Hairline()

            Spacer(Modifier.height(32.dp))
            SettingsSection(settings)

            Spacer(Modifier.height(32.dp))
            SecondaryButton(
                text = stringResource(R.string.account_sign_out),
                onClick = onSignOut,
                enabled = idle,
                loading = pending == AuthAction.SignOut,
            )
            if (signOutError != null) FieldError(signOutError, Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(8.dp))
            DestructiveButton(
                text = stringResource(R.string.account_delete),
                onClick = { confirmDelete = true },
                enabled = idle,
                loading = !needsReauth && (pending == AuthAction.Delete || pending == AuthAction.Reauth),
                modifier = Modifier.fillMaxWidth(),
            )
            if (deleteError != null) FieldError(deleteError, Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Shield, null, Modifier.size(16.dp).padding(top = 2.dp), tint = c.pencil)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.account_privacy),
                    style = ScrubType.Small,
                    color = c.pencil,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.account_delete_title), style = ScrubType.SectionTitle, color = c.graphite) },
            text = {
                Text(
                    stringResource(R.string.account_delete_body),
                    style = ScrubType.Body,
                    color = c.pencil,
                )
            },
            confirmButton = {
                DestructiveButton(stringResource(R.string.account_delete), onClick = {
                    confirmDelete = false
                    onDelete()
                })
            },
            dismissButton = { QuietButton(stringResource(R.string.action_cancel), onClick = { confirmDelete = false }) },
            shape = DialogShape,
            containerColor = c.paper,
            tonalElevation = 0.dp,
        )
    }

    if (needsReauth) {
        ReauthDialog(
            method = user.method,
            pending = pending == AuthAction.Reauth,
            error = errorText(error?.messageRes?.takeIf { errorFrom == AuthAction.Reauth }),
            onPassword = onReauthWithPassword,
            onGoogle = onReauthWithGoogle,
            onCancel = onCancelReauth,
        )
    }
}

/** Account screen for someone using Scrub without an account. */
@Composable
fun GuestAccountScreen(settings: AppSettingsUi, onBack: () -> Unit, onSignIn: () -> Unit) {
    val c = ScrubTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopBar(stringResource(R.string.guest_title), onBack = onBack)
        Column(Modifier.padding(horizontal = Gutter)) {
            Spacer(Modifier.height(16.dp))
            Avatar(initial = null, photoUrl = null, size = 64.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.guest_body),
                style = ScrubType.Body,
                color = c.pencil,
            )
            Spacer(Modifier.height(32.dp))
            PrimaryButton(stringResource(R.string.guest_sign_in), onClick = onSignIn)
            Spacer(Modifier.height(40.dp))
            SettingsSection(settings)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val c = ScrubTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = ScrubType.Small, color = c.pencil)
        Text(value, style = ScrubType.Body, color = c.graphite)
    }
}

/** Firebase needs a recent sign-in before deleting. Ask again, then the delete is retried. */
@Composable
private fun ReauthDialog(
    method: SignInMethod,
    pending: Boolean,
    error: String?,
    onPassword: (String) -> Unit,
    onGoogle: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = ScrubTheme.colors
    var password by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf(false) }
    val emptyError = if (checked && password.isEmpty()) stringResource(R.string.validation_enter_password) else null

    fun submit() {
        checked = true
        if (password.isNotEmpty()) onPassword(password)
    }

    AlertDialog(
        onDismissRequest = { if (!pending) onCancel() },
        title = { Text(stringResource(R.string.account_reauth_title), style = ScrubType.SectionTitle, color = c.graphite) },
        text = {
            Column(Modifier.imePadding()) {
                Text(
                    if (method == SignInMethod.Google) stringResource(R.string.account_reauth_google)
                    else stringResource(R.string.account_reauth_password),
                    style = ScrubType.Body,
                    color = c.pencil,
                )
                if (method == SignInMethod.Email) {
                    Spacer(Modifier.height(16.dp))
                    ScrubTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.field_password),
                        kind = FieldKind.Password,
                        error = emptyError ?: error,
                        imeAction = ImeAction.Done,
                        onDone = ::submit,
                        enabled = !pending,
                    )
                } else if (error != null) {
                    FieldError(error, Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            DestructiveButton(
                text = if (method == SignInMethod.Google) stringResource(R.string.auth_continue_google) else stringResource(R.string.account_delete),
                onClick = { if (method == SignInMethod.Google) onGoogle() else submit() },
                loading = pending,
            )
        },
        dismissButton = { QuietButton(stringResource(R.string.action_cancel), onClick = onCancel, enabled = !pending) },
        shape = DialogShape,
        containerColor = c.paper,
        tonalElevation = 0.dp,
    )
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private val previewUser = AuthUser(
    uid = "preview",
    name = "Ada Lovelace",
    email = "ada@example.com",
    photoUrl = null,
    method = SignInMethod.Email,
    emailVerified = false,
)

@LocalizedScreenPreviews
@Composable
private fun AccountPreview() = PreviewFrame {
    AccountScreen(
        user = previewUser, pending = null, error = null, errorFrom = null, needsReauth = false,
        settings = PreviewSettings, onBack = {}, onSignOut = {}, onDelete = {}, onReauthWithPassword = {},
        onReauthWithGoogle = {}, onCancelReauth = {},
    )
}

@ScreenPreviews
@Composable
private fun AccountGooglePreview() = PreviewFrame {
    AccountScreen(
        user = previewUser.copy(method = SignInMethod.Google, emailVerified = true),
        pending = AuthAction.SignOut, error = null, errorFrom = null, needsReauth = false,
        settings = PreviewSettings, onBack = {}, onSignOut = {}, onDelete = {}, onReauthWithPassword = {},
        onReauthWithGoogle = {}, onCancelReauth = {},
    )
}

@LocalizedScreenPreviews
@Composable
private fun GuestAccountPreview() = PreviewFrame {
    GuestAccountScreen(settings = PreviewSettings, onBack = {}, onSignIn = {})
}
