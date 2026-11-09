package com.scrub.app.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scrub.app.Screen
import com.scrub.app.ScrubViewModel
import com.scrub.app.auth.AuthUser

@Composable
fun HomeScreen(
    vm: ScrubViewModel,
    onAddPhoto: () -> Unit,
    onScan: () -> Unit,
    permissionNote: String?,
    onPermissionNoteClick: () -> Unit,
    user: AuthUser?,
    onAccount: () -> Unit,
    showVerifyNote: Boolean,
    onDismissVerifyNote: () -> Unit,
) {
    val c = ScrubTheme.colors
    val hasRefs = vm.references.isNotEmpty()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gutter),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column {
                Spacer(Modifier.height(40.dp))
                Wordmark()
            }
            if (user != null) {
                Avatar(
                    initial = user.initial,
                    photoUrl = user.photoUrl,
                    size = 40.dp,
                    onClick = onAccount,
                    onClickLabel = "Open account",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp)
                        .semantics { contentDescription = "Account: ${user.displayName}" },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Clear someone out of your camera roll.",
            style = ScrubType.Body,
            color = c.pencil,
        )
        if (showVerifyNote) {
            Spacer(Modifier.height(16.dp))
            VerifyEmailNote(onDismiss = onDismissVerifyNote)
        }

        Spacer(Modifier.height(40.dp))

        Step(number = 1, title = "Add photos of them", done = hasRefs) {
            Text(
                if (!hasRefs) "Pick a clear photo of them. You'll tap their face next."
                else "Add 2 or 3 different photos of them for the most accurate results.",
                style = ScrubType.Small,
                color = c.pencil,
            )
            Spacer(Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(vm.references) { ref ->
                    Box(Modifier.size(80.dp)) {
                        FaceThumb(
                            ref.thumbnail,
                            contentDescription = "Their face",
                            modifier = Modifier
                                .size(72.dp)
                                .align(Alignment.BottomStart),
                        )
                        RemoveFaceButton(
                            onClick = { vm.removeReference(ref) },
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
                item { AddFaceSlot(onAddPhoto, hasRefs) }
            }
        }

        Step(number = 2, title = "Scan your photos", done = false) {
            Text(
                "Scrub looks through every photo on this phone for their face.",
                style = ScrubType.Small,
                color = c.pencil,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = if (hasRefs) "Scan my photos" else "Add a photo first",
                onClick = onScan,
                enabled = hasRefs,
            )
            if (permissionNote != null) {
                Spacer(Modifier.height(16.dp))
                Text(permissionNote, style = ScrubType.Small, color = c.graphite)
                QuietButton(
                    "Open settings",
                    onClick = onPermissionNoteClick,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Step(number = 3, title = "Review and remove", done = false, last = true) {
            Text(
                "You choose which photos go to the trash and which get hidden. " +
                    "Nothing is removed until you confirm.",
                style = ScrubType.Small,
                color = c.pencil,
            )
        }

        Spacer(Modifier.height(32.dp))
        Hairline()

        val n = vm.vaultItems.size
        QuietButton(
            text = if (n == 0) "Hidden photos" else "Hidden photos ($n)",
            icon = Icons.Outlined.VisibilityOff,
            onClick = { vm.screen = Screen.Vault },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Shield, null, Modifier.size(16.dp), tint = c.pencil)
            Spacer(Modifier.width(8.dp))
            Text(
                "Your photos never leave this phone.",
                style = ScrubType.Small,
                color = c.pencil,
            )
        }
    }
}

/** Dismissible reminder after signing up with email. */
@Composable
private fun VerifyEmailNote(onDismiss: () -> Unit) {
    val c = ScrubTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ButtonRadius))
            .background(c.paper)
            .border(1.dp, c.smudge, RoundedCornerShape(ButtonRadius))
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Email, null, Modifier.size(18.dp), tint = c.graphite)
        Spacer(Modifier.width(12.dp))
        Text(
            "Check your inbox to verify your email.",
            style = ScrubType.Small,
            color = c.graphite,
            modifier = Modifier.weight(1f),
        )
        IconAction(Icons.Filled.Close, "Dismiss", onDismiss)
    }
}

/**
 * One numbered step. The number sits in a circle on the left, joined to the next step by a
 * hairline. A finished step shows an eraser-pink tick instead of its number.
 */
@Composable
private fun Step(
    number: Int,
    title: String,
    done: Boolean,
    last: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = ScrubTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                // Hairline joining this step's number to the next one
                if (!last) {
                    val x = 16.dp.toPx()
                    drawLine(
                        color = c.smudge,
                        start = Offset(x, 40.dp.toPx()),
                        end = Offset(x, size.height - 8.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            },
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .then(
                    if (done) Modifier.background(c.eraser)
                    else Modifier.border(1.dp, c.graphite, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Filled.Check, "Done", tint = c.onEraser, modifier = Modifier.size(18.dp))
            } else {
                Text("$number", style = ScrubType.Button, color = c.graphite)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(bottom = if (last) 0.dp else 32.dp)
        ) {
            Text(
                title,
                style = ScrubType.SectionTitle,
                color = c.graphite,
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .padding(top = 4.dp)
                    .semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

/** Empty round slot for adding another face. */
@Composable
private fun AddFaceSlot(onClick: () -> Unit, hasRefs: Boolean) {
    val c = ScrubTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(Modifier.size(80.dp), contentAlignment = Alignment.BottomStart) {
        Box(
            Modifier
                .size(72.dp)
                .focusRing(focused, c.graphite, null)
                .clip(CircleShape)
                .background(c.paper)
                .border(1.dp, c.smudge, CircleShape)
                .clickable(
                    interactionSource = source,
                    indication = ripple(color = c.graphite),
                    role = Role.Button,
                    onClickLabel = if (hasRefs) "Add another photo of them" else "Add a photo of them",
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = if (hasRefs) "Add another photo of them" else "Add a photo of them",
                tint = c.graphite,
            )
        }
    }
}

/** Small round "x" on the corner of a face. Drawn at 24dp inside a 48dp touch target. */
@Composable
private fun RemoveFaceButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ScrubTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier
            .size(48.dp)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = "Remove this face",
                onClick = onClick,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .focusRing(focused, c.graphite, null)
                .clip(CircleShape)
                .background(c.graphite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, "Remove this face", tint = c.chalk, modifier = Modifier.size(14.dp))
        }
    }
}

