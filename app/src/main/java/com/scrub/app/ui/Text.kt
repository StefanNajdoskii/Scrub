package com.scrub.app.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.scrub.app.UiText
import java.text.NumberFormat
import java.util.Locale

// Text helpers. Every user-visible string lives in strings.xml or plurals.xml (values/ for
// English, values-mk/ for Macedonian). Counts are passed as %1$s, already formatted for the
// locale ("4,812" / "4.812"), and the plural form is picked from the raw number.

/** The language the UI is shown in right now (the per-app language when one is set). */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** [n] with the locale's digit grouping. */
@Composable
@ReadOnlyComposable
fun formatCount(n: Int): String = NumberFormat.getIntegerInstance(currentLocale()).format(n)

/** [fraction] 0..1 as a whole percentage in the locale's style ("55%", "55 %"). */
@Composable
@ReadOnlyComposable
fun formatPercent(fraction: Float): String =
    NumberFormat.getPercentInstance(currentLocale()).format(fraction.toDouble())

/** A plural whose %1$s is [count]; [more] fill %2$s onwards. */
@Composable
fun pluralCount(@PluralsRes id: Int, count: Int, vararg more: Any): String =
    pluralStringResource(id, count, formatCount(count), *more)

/** Private-use character that marks where the count sits, so the text can be split around it. */
private const val CountMark = ""

/**
 * A plural split around its %1$s, for text where only the number rolls (titles, captions,
 * buttons). The words around it come from the translation, so their order can change.
 */
@Composable
fun countLabel(@PluralsRes id: Int, count: Int, vararg more: Any): CountLabel {
    val text = pluralStringResource(id, count, CountMark, *more)
    val i = text.indexOf(CountMark)
    return if (i < 0) CountLabel(text, count, showCount = false)
    else CountLabel(text.substring(0, i), count, text.substring(i + CountMark.length))
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Res -> stringResource(id)
    is UiText.Count -> pluralCount(id, count)
}

/** A validation result (a string resource id, or null when the value is fine) as text. */
@Composable
fun errorText(@StringRes id: Int?): String? = id?.let { stringResource(it) }
