package com.scrub.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private val FieldShape = RoundedCornerShape(ButtonRadius)

/** What kind of value a [ScrubTextField] holds: sets the keyboard and autofill hints. */
enum class FieldKind { Name, Email, Password, NewPassword }

/**
 * Text field in the pencil-and-eraser style: label above, Paper box with a Smudge outline
 * (Graphite when focused, Ink red with an error), then the error or a hint below.
 *
 * [imeAction] Next moves focus to the next field; Done calls [onDone].
 * Password fields get a show/hide eye.
 */
@Composable
fun ScrubTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    kind: FieldKind,
    modifier: Modifier = Modifier,
    error: String? = null,
    hint: String? = null,
    hintMet: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    enabled: Boolean = true,
) {
    val c = ScrubTheme.colors
    val focusManager = LocalFocusManager.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val isPassword = kind == FieldKind.Password || kind == FieldKind.NewPassword
    var visible by rememberSaveable { mutableStateOf(false) }

    val borderColor = when {
        error != null -> c.inkRed
        focused -> c.graphite
        else -> c.smudge
    }
    val keyboard = KeyboardOptions(
        capitalization = if (kind == FieldKind.Name) KeyboardCapitalization.Words else KeyboardCapitalization.None,
        autoCorrectEnabled = kind == FieldKind.Name,
        keyboardType = when (kind) {
            FieldKind.Name -> KeyboardType.Text
            FieldKind.Email -> KeyboardType.Email
            FieldKind.Password, FieldKind.NewPassword ->
                if (visible) KeyboardType.Text else KeyboardType.Password
        },
        imeAction = imeAction,
    )
    val autofill = when (kind) {
        FieldKind.Name -> ContentType.PersonFullName
        FieldKind.Email -> ContentType.EmailAddress
        FieldKind.Password -> ContentType.Password
        FieldKind.NewPassword -> ContentType.NewPassword
    }

    Column(modifier.fillMaxWidth()) {
        Text(label, style = ScrubType.Small, color = c.graphite)
        Spacer(Modifier.size(6.dp))
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = c.graphite,
                backgroundColor = c.eraser.copy(alpha = 0.45f),
            )
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = ScrubType.Body.copy(color = if (enabled) c.graphite else c.pencil),
                cursorBrush = SolidColor(c.graphite),
                keyboardOptions = keyboard,
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = { onDone() },
                ),
                visualTransformation = if (isPassword && !visible) PasswordVisualTransformation()
                else VisualTransformation.None,
                interactionSource = source,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentType = autofill
                        contentDescription = label
                        if (error != null) error(error)
                    },
                decorationBox = { inner ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(FieldShape)
                            .background(c.paper)
                            .border(if (focused || error != null) 1.5.dp else 1.dp, borderColor, FieldShape)
                            .padding(start = 16.dp, end = if (isPassword) 4.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) { inner() }
                        if (isPassword) {
                            IconAction(
                                icon = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (visible) "Hide password" else "Show password",
                                onClick = { visible = !visible },
                            )
                        }
                    }
                },
            )
        }
        when {
            error != null -> FieldError(error, Modifier.padding(top = 6.dp))
            hint != null -> Row(
                Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hintMet) {
                    Icon(Icons.Filled.Check, null, tint = c.graphite, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(hint, style = ScrubType.Small, color = if (hintMet) c.graphite else c.pencil)
            }
        }
    }
}

/** Ink red message under a field or a form. Read out by screen readers when it appears. */
@Composable
fun FieldError(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        style = ScrubType.Small,
        color = ScrubTheme.colors.inkRed,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Hairline with a word in the middle: ———— or ————. */
@Composable
fun LabeledDivider(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Hairline(Modifier.weight(1f))
        Text(
            text,
            style = ScrubType.Small,
            color = ScrubTheme.colors.pencil,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Hairline(Modifier.weight(1f))
    }
}

/**
 * Round account picture: a Chalk circle with a 2dp Eraser ring and the initial in Graphite
 * (Bricolage SemiBold), or the Google profile photo inside the same ring.
 * Clickable when [onClick] is set.
 */
@Composable
fun Avatar(
    initial: String,
    photoUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentDescription: String? = null,
) {
    val c = ScrubTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier
            .size(size)
            .focusRing(focused, c.graphite, null)
            .clip(CircleShape)
            .background(c.chalk)
            .then(
                if (contentDescription != null) Modifier.clearAndSetSemantics {
                    this.contentDescription = contentDescription
                    if (onClick != null) {
                        role = Role.Button
                        onClick(label = onClickLabel) { onClick(); true }
                    }
                } else Modifier
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = source,
                    indication = ripple(color = c.graphite),
                    role = Role.Button,
                    onClickLabel = onClickLabel,
                    onClick = onClick,
                ) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            style = if (size >= 56.dp) ScrubType.ScreenTitle else ScrubType.SectionTitle,
            color = c.graphite,
        )
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Ring on top, so it frames the photo too.
        Box(
            Modifier
                .matchParentSize()
                .border(2.dp, c.eraser, CircleShape)
        )
    }
}

/** Finds the hosting Activity (needed by Credential Manager to show the Google account picker). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

/** Light + dark preview pair for full screens. */
@Preview(name = "Light", showBackground = true, widthDp = 360, heightDp = 780)
@Preview(
    name = "Dark", showBackground = true, widthDp = 360, heightDp = 780,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ScreenPreviews

/** Theme + background for previews. */
@Composable
fun PreviewFrame(content: @Composable () -> Unit) {
    ScrubTheme {
        Surface(color = ScrubTheme.colors.chalk, contentColor = ScrubTheme.colors.graphite) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
@Preview(name = "Fields light", showBackground = true, widthDp = 360)
@Preview(
    name = "Fields dark", showBackground = true, widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
private fun FieldsPreview() = ScrubTheme {
    Surface(color = ScrubTheme.colors.chalk) {
        Column(Modifier.padding(Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScrubTextField("ada@example", {}, "Email", FieldKind.Email, error = "Enter a valid email address.")
            ScrubTextField("hunter2", {}, "Password", FieldKind.NewPassword, hint = "At least 8 characters")
            LabeledDivider("or")
            Avatar("A", null, 40.dp)
        }
    }
}
