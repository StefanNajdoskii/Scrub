package com.scrub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scrub.app.R
import com.scrub.app.settings.AppLanguage
import com.scrub.app.settings.ThemeMode

/** What the Appearance and Language rows on the Account screens show and do. */
@Immutable
class AppSettingsUi(
    val theme: ThemeMode,
    /** The chosen language; [AppLanguage.Phone] when following the phone. */
    val language: AppLanguage,
    val onThemeChange: (ThemeMode) -> Unit,
    /**
     * Opens Android's own per-app language screen (Android 13+) and returns true. Null, or
     * false when the screen couldn't be opened: then the Language row opens Scrub's small
     * picker, which calls [onLanguageChange].
     */
    val openSystemLanguage: (() -> Boolean)?,
    val onLanguageChange: (AppLanguage) -> Unit,
)

/** Appearance (System / Light / Dark) and Language, for signed-in users and guests alike. */
@Composable
fun SettingsSection(settings: AppSettingsUi, modifier: Modifier = Modifier) {
    val c = ScrubTheme.colors
    var picking by rememberSaveable { mutableStateOf(false) }
    val modes = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)

    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.settings_appearance),
            style = ScrubType.SectionTitle,
            color = c.graphite,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        SegmentedControl(
            options = listOf(
                stringResource(R.string.appearance_system),
                stringResource(R.string.appearance_light),
                stringResource(R.string.appearance_dark),
            ),
            selectedIndex = modes.indexOf(settings.theme),
            onSelect = { settings.onThemeChange(modes[it]) },
        )
        Spacer(Modifier.height(24.dp))
        Hairline()
        LanguageRow(
            value = languageLabel(settings.language),
            onClick = { if (settings.openSystemLanguage?.invoke() != true) picking = true },
        )
        Hairline()
    }

    if (picking) {
        LanguageDialog(
            current = settings.language,
            onPick = {
                picking = false
                if (it != settings.language) settings.onLanguageChange(it)
            },
            onDismiss = { picking = false },
        )
    }
}

/** "English", "Македонски", or "Phone language (English)". Language names are in their own language. */
@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.Phone -> stringResource(R.string.language_phone_current, stringResource(R.string.language_current))
    AppLanguage.English -> stringResource(R.string.language_name_en)
    AppLanguage.Macedonian -> stringResource(R.string.language_name_mk)
}

@Composable
private fun LanguageRow(value: String, onClick: () -> Unit) {
    val c = ScrubTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .heightIn(min = 64.dp)
            .focusRing(focused, c.graphite, ButtonRadius)
            .clip(RoundedCornerShape(ButtonRadius))
            .clickable(
                interactionSource = source,
                indication = ripple(color = c.graphite),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_language), style = ScrubType.Small, color = c.pencil)
            Text(value, style = ScrubType.Body, color = c.graphite)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = c.pencil)
    }
}

/** Android 11-12 only (13+ uses the system screen): pick a language, applied straight away. */
@Composable
private fun LanguageDialog(current: AppLanguage, onPick: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    val c = ScrubTheme.colors
    val options = listOf(
        AppLanguage.Phone to stringResource(R.string.language_phone),
        AppLanguage.English to stringResource(R.string.language_name_en),
        AppLanguage.Macedonian to stringResource(R.string.language_name_mk),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language), style = ScrubType.SectionTitle, color = c.graphite) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { (language, label) ->
                    val selected = language == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(ButtonRadius))
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onPick(language) },
                            )
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = ScrubType.Body, color = c.graphite, modifier = Modifier.weight(1f))
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.Check, null, Modifier.size(20.dp), tint = c.graphite)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { QuietButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
        shape = DialogShape,
        containerColor = c.paper,
        tonalElevation = 0.dp,
    )
}

/** Settings as previews show them. */
val PreviewSettings = AppSettingsUi(
    theme = ThemeMode.System,
    language = AppLanguage.Phone,
    onThemeChange = {},
    openSystemLanguage = null,
    onLanguageChange = {},
)
