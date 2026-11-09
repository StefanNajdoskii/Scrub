package com.scrub.app.auth

/** Field checks that run before anything is sent to Firebase. Each returns an error or null. */
object AuthValidation {
    const val MIN_PASSWORD = 8

    private val emailShape = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun name(value: String): String? =
        if (value.isBlank()) "Enter your name." else null

    fun email(value: String): String? = when {
        value.isBlank() -> "Enter your email."
        !emailShape.matches(value.trim()) -> "Enter a valid email address."
        else -> null
    }

    /** For signing in: any non-empty password (old accounts may predate the length rule). */
    fun existingPassword(value: String): String? =
        if (value.isEmpty()) "Enter your password." else null

    /** For new passwords. */
    fun newPassword(value: String): String? = when {
        value.isEmpty() -> "Choose a password."
        value.length < MIN_PASSWORD -> AuthRepository.WEAK_PASSWORD
        else -> null
    }

    fun confirmPassword(password: String, confirm: String): String? = when {
        confirm.isEmpty() -> "Type your password again."
        confirm != password -> "Passwords don't match."
        else -> null
    }
}
