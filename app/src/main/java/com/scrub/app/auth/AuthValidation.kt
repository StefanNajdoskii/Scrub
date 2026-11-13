package com.scrub.app.auth

import androidx.annotation.StringRes
import com.scrub.app.R

/**
 * Field checks that run before anything is sent to Firebase. Each returns the error message
 * (a string resource) or null.
 */
object AuthValidation {
    /** Also written out as "8" in the password strings in strings.xml / plurals.xml. */
    const val MIN_PASSWORD = 8

    private val emailShape = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    @StringRes
    fun name(value: String): Int? =
        if (value.isBlank()) R.string.validation_enter_name else null

    @StringRes
    fun email(value: String): Int? = when {
        value.isBlank() -> R.string.validation_enter_email
        !emailShape.matches(value.trim()) -> R.string.validation_invalid_email
        else -> null
    }

    /** For signing in: any non-empty password (old accounts may predate the length rule). */
    @StringRes
    fun existingPassword(value: String): Int? =
        if (value.isEmpty()) R.string.validation_enter_password else null

    /** For new passwords. */
    @StringRes
    fun newPassword(value: String): Int? = when {
        value.isEmpty() -> R.string.validation_choose_password
        value.length < MIN_PASSWORD -> AuthRepository.WEAK_PASSWORD
        else -> null
    }

    @StringRes
    fun confirmPassword(password: String, confirm: String): Int? = when {
        confirm.isEmpty() -> R.string.validation_retype_password
        confirm != password -> R.string.validation_passwords_differ
        else -> null
    }
}
