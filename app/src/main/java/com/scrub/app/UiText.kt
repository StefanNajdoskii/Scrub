package com.scrub.app

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Text chosen by a ViewModel but resolved in the UI, so it's always in the current language
 * (and changes with it) without the ViewModel holding a Context. See ui/Text.kt for resolve().
 */
sealed interface UiText {
    data class Res(@param:StringRes val id: Int) : UiText

    /** A plural whose %1$s is [count], formatted for the locale. */
    data class Count(@param:PluralsRes val id: Int, val count: Int) : UiText
}
