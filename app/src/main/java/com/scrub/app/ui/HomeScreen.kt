package com.scrub.app.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.NoEncryption
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrub.app.RefFace
import com.scrub.app.Screen
import com.scrub.app.ScrubViewModel
import com.scrub.app.auth.AuthUser
import com.scrub.app.auth.SignInMethod

/** Size of a reference face and of the add-photo slot, so they line up in one row. */
private val FaceSize = 72.dp

/** Circle size of the step indicators. */
private val StepDot = 28.dp

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
    onOpenMatchLab: (() -> Unit)? = null,
) = HomeContent(
    references = vm.references,
    hiddenCount = vm.vaultItems.size,
    onAddPhoto = onAddPhoto,
    onRemoveReference = vm::removeReference,
    onScan = onScan,
    onOpenHidden = { vm.screen = Screen.Vault },
    permissionNote = permissionNote,
    onPermissionNoteClick = onPermissionNoteClick,
    user = user,
    onAccount = onAccount,
    showVerifyNote = showVerifyNote,
    onDismissVerifyNote = onDismissVerifyNote,
    onOpenMatchLab = onOpenMatchLab,
)

/** Stateless Home, so it can be previewed without a ViewModel. */
@Composable
private fun HomeContent(
    references: List<RefFace>,
    hiddenCount: Int,
    onAddPhoto: () -> Unit,
    onRemoveReference: (RefFace) -> Unit,
    onScan: () -> Unit,
    onOpenHidden: () -> Unit,
    permissionNote: String?,
    onPermissionNoteClick: () -> Unit,
    user: AuthUser?,
    onAccount: () -> Unit,
    showVerifyNote: Boolean,
    onDismissVerifyNote: () -> Unit,
    onOpenMatchLab: (() -> Unit)? = null,
) {
    val c = ScrubTheme.colors
    val hasRefs = references.isNotEmpty()
    val animationsOff = rememberAnimationsOff()

    // Content on top, footer pinned to the bottom when there's room; scrolls when there isn't.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = Gutter),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    // Debug builds pass onOpenMatchLab: long-press the wordmark for the Match lab.
                    Wordmark(
                        Modifier
                            .padding(top = 32.dp)
                            .then(
                                if (onOpenMatchLab == null) Modifier
                                else Modifier.pointerInput(onOpenMatchLab) {
                                    detectTapGestures(onLongPress = { onOpenMatchLab() })
                                }
                            )
                    )
                    Spacer(Modifier.weight(1f))
                    // Guests get a person outline; it opens the "Not signed in" account screen.
                    Avatar(
                        initial = user?.initial,
                        photoUrl = user?.photoUrl,
                        size = 40.dp,
                        onClick = onAccount,
                        onClickLabel = "Open account",
                        contentDescription = if (user != null) "Account" else "Account, not signed in",
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Clear someone out of your camera roll.",
                    style = ScrubType.Body,
                    color = c.pencil,
                )
                if (showVerifyNote) {
                    Spacer(Modifier.height(16.dp))
                    VerifyEmailNote(onDismiss = onDismissVerifyNote)
                }

                Spacer(Modifier.height(32.dp))

                Step(
                    number = 1,
                    title = "Add photos of them",
                    description = if (!hasRefs) "Pick a clear photo of them. You'll tap their face next."
                    else "Add 2 or 3 different photos of them for the most accurate results.",
                    state = if (hasRefs) StepState.Done else StepState.Active,
                ) {
                    Spacer(Modifier.height(16.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(references) { ref ->
                            // 8dp extra on top/right leaves room for the remove "x".
                            Box(Modifier.size(FaceSize + 8.dp)) {
                                FaceThumb(
                                    ref.thumbnail,
                                    contentDescription = "Their face",
                                    modifier = Modifier
                                        .size(FaceSize)
                                        .align(Alignment.BottomStart),
                                )
                                RemoveFaceButton(
                                    onClick = { onRemoveReference(ref) },
                                    modifier = Modifier.align(Alignment.TopEnd),
                                )
                            }
                        }
                        item { AddPhotoSlot(onAddPhoto) }
                    }
                }

                Step(
                    number = 2,
                    title = "Scan your photos",
                    description = "Scrub looks through every photo on this phone for their face.",
                    state = if (hasRefs) StepState.Active else StepState.Upcoming,
                ) {
                    AnimatedVisibility(
                        visible = hasRefs,
                        enter = if (animationsOff) EnterTransition.None
                        else fadeIn(tween(220)) + expandVertically(tween(220)),
                        exit = if (animationsOff) ExitTransition.None
                        else fadeOut(tween(150)) + shrinkVertically(tween(150)),
                    ) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(text = "Scan my photos", onClick = onScan)
                        }
                    }
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

                Step(
                    number = 3,
                    title = "Review and remove",
                    description = "You choose which photos go to the trash and which get hidden. " +
                        "Nothing is removed until you confirm.",
                    state = StepState.Upcoming,
                    last = true,
                )
            }

            // Footer
            Column(Modifier.padding(top = 32.dp)) {
                Hairline()
                HiddenPhotosRow(count = hiddenCount, onClick = onOpenHidden)
                Hairline()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 24.dp),
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
        Icon(Icons.Outlined.Email, null, Modifier.size(20.dp), tint = c.graphite)
        Spacer(Modifier.width(16.dp))
        Text(
            "Check your inbox to verify your email.",
            style = ScrubType.Small,
            color = c.graphite,
            modifier = Modifier.weight(1f),
        )
        IconAction(Icons.Filled.Close, "Dismiss", onDismiss)
    }
}

private enum class StepState { Done, Active, Upcoming }

/**
 * One numbered step. The indicator sits on the left, joined to the next step by a line:
 * Graphite once this step is done (progress has reached the next one), Smudge otherwise.
 * - Active: Graphite circle, Chalk number.
 * - Done: Eraser circle with a check.
 * - Upcoming: Smudge outline, and number, title and description all in Pencil.
 */
@Composable
private fun Step(
    number: Int,
    title: String,
    description: String,
    state: StepState,
    last: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val c = ScrubTheme.colors
    val textColor = if (state == StepState.Upcoming) c.pencil else c.graphite
    val lineColor = if (state == StepState.Done) c.graphite else c.smudge
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (!last) {
                    val x = (StepDot / 2).toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(x, (StepDot + 6.dp).toPx()),
                        end = Offset(x, size.height - 6.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            },
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(StepDot)
                .clip(CircleShape)
                .then(
                    when (state) {
                        StepState.Done -> Modifier.background(c.eraser)
                        StepState.Active -> Modifier.background(c.graphite)
                        StepState.Upcoming -> Modifier.border(1.dp, c.smudge, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                // onEraser is the Graphite ink in both themes, so the check stays readable on pink.
                StepState.Done ->
                    Icon(Icons.Filled.Check, "Done", tint = c.onEraser, modifier = Modifier.size(16.dp))
                StepState.Active ->
                    Text("$number", style = ScrubType.StepNumber, color = c.chalk)
                StepState.Upcoming ->
                    Text("$number", style = ScrubType.StepNumber, color = c.pencil)
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
                color = textColor,
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .padding(top = 4.dp)
                    .semantics {
                        heading()
                        if (state == StepState.Upcoming) contentDescription = "$title, not available yet"
                    },
            )
            Text(description, style = ScrubType.Small, color = textColor)
            content()
        }
    }
}

/**
 * Dashed round slot for adding a photo, the same size as a reference face, with "Add photo"
 * under it. The whole slot (circle + label) is one touch target.
 */
@Composable
private fun AddPhotoSlot(onClick: () -> Unit) {
    val c = ScrubTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(
        Modifier
            .clip(RoundedCornerShape(ButtonRadius))
            .clickable(
                interactionSource = source,
                indication = ripple(color = c.graphite),
                role = Role.Button,
                onClick = onClick,
            )
            .clearAndSetSemantics { contentDescription = "Add a photo of them" }
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Same 8dp top offset as the face thumbnails, so the circles line up.
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .size(FaceSize)
                .focusRing(focused, c.graphite, null)
                .drawBehind {
                    val w = 1.5.dp.toPx()
                    drawCircle(
                        color = c.pencil,
                        radius = size.minDimension / 2f - w / 2f,
                        style = Stroke(
                            width = w,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = c.graphite)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Add photo",
            style = ScrubType.Small,
            color = c.pencil,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(FaceSize),
        )
    }
}

/** Full-width 56dp row into the vault: icon, label, count (hidden at 0), chevron. */
@Composable
private fun HiddenPhotosRow(count: Int, onClick: () -> Unit) {
    val c = ScrubTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(56.dp)
            .focusRing(focused, c.graphite, ButtonRadius)
            .clip(RoundedCornerShape(ButtonRadius))
            .background(if (focused) c.smudge.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(
                interactionSource = source,
                indication = ripple(color = c.graphite),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.NoEncryption, null, Modifier.size(20.dp), tint = c.graphite)
        Spacer(Modifier.width(16.dp))
        Text("Hidden photos", style = ScrubType.Button, color = c.graphite, modifier = Modifier.weight(1f))
        if (count > 0) {
            Text(
                "$count",
                style = ScrubType.Body,
                color = c.pencil,
                modifier = Modifier.semantics { contentDescription = ScrubViewModel.plural(count) },
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = c.pencil)
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

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private val previewUser = AuthUser(
    uid = "preview", name = "Ada Lovelace", email = "ada@example.com", photoUrl = null,
    method = SignInMethod.Email, emailVerified = true,
)

private fun previewFace(): RefFace {
    val bmp = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFB9A89A.toInt()) }
    return RefFace(bmp, FloatArray(0))
}

@Composable
private fun HomePreview(references: List<RefFace>, hiddenCount: Int) = PreviewFrame {
    HomeContent(
        references = references, hiddenCount = hiddenCount,
        onAddPhoto = {}, onRemoveReference = {}, onScan = {}, onOpenHidden = {},
        permissionNote = null, onPermissionNoteClick = {},
        user = previewUser, onAccount = {}, showVerifyNote = false, onDismissVerifyNote = {},
    )
}

@Preview(name = "No faces", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HomeNoFacesPreview() = HomePreview(references = emptyList(), hiddenCount = 0)

@Preview(name = "One face", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HomeOneFacePreview() = HomePreview(references = listOf(previewFace()), hiddenCount = 3)

@Preview(
    name = "Dark, one face", showBackground = true, widthDp = 360, heightDp = 640,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun HomeDarkPreview() = HomePreview(references = listOf(previewFace()), hiddenCount = 3)
