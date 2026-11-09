package com.scrub.app.ui

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// ---------------------------------------------------------------------------------------------
// Shape and spacing
// ---------------------------------------------------------------------------------------------

/** Screen side padding. */
val Gutter = 24.dp
val ButtonRadius = 14.dp
val TileRadius = 6.dp
private val ButtonShape = RoundedCornerShape(ButtonRadius)
private val TileShape = RoundedCornerShape(TileRadius)

/**
 * Draws a 2dp ring just outside the element while it has keyboard / D-pad focus.
 * Pass `cornerRadius = null` for round elements. Put it before any `clip` in the chain.
 */
fun Modifier.focusRing(focused: Boolean, color: Color, cornerRadius: Dp?): Modifier = drawWithContent {
    drawContent()
    if (focused) {
        val gap = 3.dp.toPx()
        val w = 2.dp.toPx()
        val r = cornerRadius?.toPx() ?: (size.minDimension / 2f)
        drawRoundRect(
            color = color,
            topLeft = Offset(-gap, -gap),
            size = Size(size.width + gap * 2, size.height + gap * 2),
            cornerRadius = CornerRadius(r + gap),
            style = Stroke(w),
        )
    }
}

/** 1dp Smudge line, the only separator used in the app. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ScrubTheme.colors.smudge)
    )
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

private enum class ButtonKind { Primary, Secondary }

/** Small spinner shown inside a button while its request runs. */
@Composable
private fun ButtonSpinner(color: Color, size: Dp) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        trackColor = Color.Transparent,
        strokeWidth = 2.dp,
    )
}

@Composable
private fun FilledButton(
    kind: ButtonKind,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: ImageVector?,
    painter: Painter? = null,
    loading: Boolean = false,
) {
    val c = ScrubTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val fill: Color
    val content: Color
    val outline: Color?
    when {
        // A loading button keeps its colors so it's clear which one is working.
        !enabled && !loading -> { fill = c.smudge; content = c.pencil; outline = null }
        kind == ButtonKind.Primary -> { fill = c.eraser; content = c.onEraser; outline = null }
        else -> { fill = Color.Transparent; content = c.graphite; outline = c.graphite }
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .focusRing(focused, c.graphite, ButtonRadius)
            .clip(ButtonShape)
            .background(fill)
            .then(if (outline != null) Modifier.border(1.dp, outline, ButtonShape) else Modifier)
            .clickable(
                interactionSource = source,
                indication = ripple(color = content),
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            loading -> {
                ButtonSpinner(content, 20.dp)
                Spacer(Modifier.width(12.dp))
            }
            painter != null -> {
                Image(
                    painter,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .alpha(if (enabled) 1f else 0.5f),
                )
                Spacer(Modifier.width(12.dp))
            }
            icon != null -> {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(text, style = ScrubType.Button, color = content, maxLines = 1)
    }
}

/** Eraser fill, Graphite text, 56dp, full width. [loading] shows a spinner and blocks taps. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
) = FilledButton(ButtonKind.Primary, text, onClick, modifier, enabled, icon, loading = loading)

/**
 * Transparent with a 1dp Graphite outline. Use [painter] for a multicolor logo (drawn
 * untinted), [icon] for a Graphite icon.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    painter: Painter? = null,
    loading: Boolean = false,
) = FilledButton(ButtonKind.Secondary, text, onClick, modifier, enabled, icon, painter, loading)

@Composable
private fun PlainTextButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: ImageVector?,
    loading: Boolean = false,
) {
    val c = ScrubTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val content = if (enabled || loading) color else c.pencil
    Row(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .focusRing(focused, c.graphite, ButtonRadius)
            .clip(ButtonShape)
            .clickable(
                interactionSource = source,
                indication = ripple(color = content),
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            ButtonSpinner(content, 16.dp)
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = ScrubType.Button, color = content)
    }
}

/** Ink red text button. Only for permanent actions: "Delete forever", "Delete account". */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) = PlainTextButton(text, ScrubTheme.colors.inkRed, onClick, modifier, enabled, null, loading)

/** Graphite text button for small actions like "Select all" or "Cancel". */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
) = PlainTextButton(text, ScrubTheme.colors.graphite, onClick, modifier, enabled, icon, loading)

/** 48dp round icon button. */
@Composable
fun IconAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = ScrubTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier
            .size(48.dp)
            .focusRing(focused, c.graphite, null)
            .clip(CircleShape)
            .clickable(
                interactionSource = source,
                indication = ripple(color = c.graphite),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = c.graphite, modifier = Modifier.size(24.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Wordmark
// ---------------------------------------------------------------------------------------------

/**
 * True when the system "Remove animations" setting is on (animator scale 0), or in previews.
 * Skip decorative motion when it is.
 */
@Composable
fun rememberAnimationsOff(): Boolean {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    return remember {
        inPreview || runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** The swipe plays once per app launch, not every time Home comes back. */
private var wordmarkSwept = false

/**
 * "scrub" in Graphite with an Eraser-pink swipe behind the lower half of the letters.
 * On first open the swipe sweeps in left to right (600ms, ease-out), unless the system has
 * animations turned off. `animate = false` draws it already swept (startup screen, previews).
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier, animate: Boolean = true) {
    val c = ScrubTheme.colors
    val animationsOff = rememberAnimationsOff()
    val progress = remember { Animatable(if (!animate || wordmarkSwept || animationsOff) 1f else 0f) }
    LaunchedEffect(Unit) {
        // A static wordmark doesn't use up the one sweep per launch.
        if (!animate) return@LaunchedEffect
        wordmarkSwept = true
        if (progress.value < 1f) progress.animateTo(1f, tween(durationMillis = 600, easing = EaseOutCubic))
    }
    var baseline by remember { mutableFloatStateOf(0f) }

    Text(
        "scrub",
        style = ScrubType.Wordmark,
        color = c.graphite,
        onTextLayout = { baseline = it.firstBaseline },
        modifier = modifier
            .semantics { heading() }
            .drawBehind {
                val p = progress.value
                if (p <= 0f || baseline <= 0f) return@drawBehind
                val em = ScrubType.Wordmark.fontSize.toPx()
                val thickness = em * 0.28f
                val centerY = baseline - em * 0.13f
                val tilt = em * 0.045f                       // rises slightly left to right
                val startX = thickness / 2f - em * 0.05f
                val endX = size.width - thickness / 2f + em * 0.05f
                val x = startX + (endX - startX) * p
                val t = (x - startX) / (endX - startX)
                drawLine(
                    color = c.eraser,
                    start = Offset(startX, centerY + tilt),
                    end = Offset(x, centerY + tilt - 2f * tilt * t),
                    strokeWidth = thickness,
                    cap = StrokeCap.Round,
                )
            },
    )
}

// ---------------------------------------------------------------------------------------------
// Structure
// ---------------------------------------------------------------------------------------------

/** Back button row with optional actions, then the screen title. */
@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val c = ScrubTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.weight(1f))
            actions()
        }
        Text(
            title,
            style = ScrubType.ScreenTitle,
            color = c.graphite,
            modifier = Modifier
                .padding(horizontal = Gutter)
                .padding(top = 8.dp, bottom = 8.dp)
                .semantics { heading() },
        )
    }
}

/** Bottom action area: Paper on Chalk, separated by a hairline. */
@Composable
fun ActionBar(caption: String? = null, content: @Composable () -> Unit) {
    val c = ScrubTheme.colors
    Column(Modifier.fillMaxWidth().background(c.paper)) {
        Hairline()
        Column(Modifier.padding(horizontal = Gutter, vertical = 16.dp)) {
            if (caption != null) {
                Text(caption, style = ScrubType.Small, color = c.pencil)
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                content()
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Photos and faces
// ---------------------------------------------------------------------------------------------

/** Tick in the corner of a photo tile. */
@Composable
fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    val c = ScrubTheme.colors
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(c.eraser)
                else Modifier
                    .background(c.photoScrim.copy(alpha = 0.3f))
                    .border(2.dp, c.onPhoto, CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = c.onEraser, modifier = Modifier.size(16.dp))
    }
}

/**
 * Square photo with small corners, like a print. `selected = null` hides the tick.
 * Unselected photos are dimmed so the selection reads at a glance.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoTile(
    model: Any,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    dimmed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val c = ScrubTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier
            .aspectRatio(1f)
            .focusRing(focused, c.graphite, TileRadius)
            .clip(TileShape)
            .background(c.paper)
            .combinedClickable(
                interactionSource = source,
                indication = ripple(color = c.onPhoto),
                onClickLabel = if (selected == null) null else if (selected) "Untick" else "Tick",
                onLongClickLabel = if (onLongClick != null) "View larger" else null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { this.contentDescription = contentDescription },
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (dimmed) 0.4f else 1f),
        )
        if (selected != null) {
            SelectionBadge(selected, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

/** Small, non-interactive photo, e.g. the live strip on the scan screen. */
@Composable
fun PhotoThumb(model: Any, modifier: Modifier = Modifier) {
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(TileShape)
            .background(ScrubTheme.colors.paper),
    )
}

/** Fully round face crop. Clickable when [onClick] is set. */
@Composable
fun FaceThumb(
    bitmap: Bitmap,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = ScrubTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .focusRing(focused, c.graphite, null)
            .clip(CircleShape)
            .background(c.paper)
            .border(1.dp, c.smudge, CircleShape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = source,
                    indication = ripple(color = c.graphite),
                    role = Role.Button,
                    onClick = onClick,
                ) else Modifier
            ),
    )
}

// ---------------------------------------------------------------------------------------------
// States
// ---------------------------------------------------------------------------------------------

/** Full-screen chalk veil with a spinner, for short blocking work. */
@Composable
fun BusyOverlay(message: String) {
    val c = ScrubTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.chalk.copy(alpha = 0.94f))
            .clickable(interactionSource = null, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spinner()
            Text(message, style = ScrubType.Body, color = c.graphite)
        }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    val c = ScrubTheme.colors
    CircularProgressIndicator(
        modifier = modifier.size(40.dp),
        color = c.graphite,
        trackColor = c.smudge,
        strokeWidth = 3.dp,
    )
}

/** Centered icon, title and text, for empty and finished states. */
@Composable
fun Hero(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    val c = ScrubTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(c.paper)
                .border(1.dp, c.smudge, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.graphite, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            title,
            style = ScrubType.ScreenTitle,
            color = c.graphite,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(body, style = ScrubType.Body, color = c.pencil, textAlign = TextAlign.Center)
    }
}
