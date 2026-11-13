package com.scrub.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scrub.app.R
import com.scrub.app.Screen
import com.scrub.app.ScrubViewModel
import kotlin.random.Random

/**
 * After Trash or Hide. [prints] are thumbnails of the affected photos, captured before the
 * action (the originals are gone from the gallery by now).
 */
@Composable
fun DoneScreen(vm: ScrubViewModel, prints: List<ImageBitmap>) {
    DoneContent(
        title = vm.doneTitle?.resolve().orEmpty(),
        body = vm.doneBody?.resolve().orEmpty(),
        prints = prints,
        othersCount = if (vm.matches.isNotEmpty()) vm.visibleMatches.size else null,
        onReviewOthers = { vm.screen = Screen.Results },
        onFinish = { vm.startOver() },
    )
}

private val PrintSize = 132.dp
private val PrintShape = RoundedCornerShape(TileRadius)

/**
 * The signature moment: the affected photos sit as a loose stack of prints, an Eraser-pink
 * swipe sweeps across them left to right and they fade and shrink away under it. Then the
 * title and body fade in, then the buttons, with one confirm haptic as the swipe ends.
 * With animations off (or no prints) it shows the end state straight away.
 */
@Composable
private fun DoneContent(
    title: String,
    body: String,
    prints: List<ImageBitmap>,
    othersCount: Int?,
    onReviewOthers: () -> Unit,
    onFinish: () -> Unit,
) {
    val c = ScrubTheme.colors
    val off = rememberAnimationsOff()
    val haptics = LocalHapticFeedback.current
    // Play once per visit, not again after rotation.
    var played by rememberSaveable { mutableStateOf(off || prints.isEmpty()) }
    val sweep = remember { Animatable(if (played) 1f else 0f) }
    val textAlpha = remember { Animatable(if (played) 1f else 0f) }
    val buttonsAlpha = remember { Animatable(if (played) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        sweep.animateTo(1f, tween(700, easing = EaseInOutCubic))
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        textAlpha.animateTo(1f, tween(200))
        buttonsAlpha.animateTo(1f, tween(200))
        played = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.graphicsLayer { alpha = textAlpha.value },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
            if (sweep.value < 1f || textAlpha.value < 1f) {
                PrintStack(prints, sweep.value, maxWidth)
            }
        }

        Column(Modifier.graphicsLayer { alpha = buttonsAlpha.value }) {
            if (othersCount != null) {
                SecondaryButton(pluralCount(R.plurals.done_review_others, othersCount), onClick = onReviewOthers)
                Spacer(Modifier.height(16.dp))
            }
            PrimaryButton(stringResource(R.string.done_finish), onClick = onFinish)
        }
    }
}

/** Up to 6 prints, loosely stacked and rotated, erased by a pink swipe at [progress] 0..1. */
@Composable
private fun PrintStack(prints: List<ImageBitmap>, progress: Float, width: androidx.compose.ui.unit.Dp) {
    val c = ScrubTheme.colors
    val density = LocalDensity.current
    // Fixed pseudo-random layout so it doesn't jump on recomposition.
    val layout = remember(prints.size) {
        val rnd = Random(prints.size * 31 + 7)
        prints.indices.map {
            Triple(
                rnd.nextFloat() * 12f - 6f,                    // rotation, -6..6 degrees
                (rnd.nextFloat() * 2f - 1f) * 56f,             // x offset, dp
                (rnd.nextFloat() * 2f - 1f) * 20f,             // y offset, dp
            )
        }
    }
    val widthPx = with(density) { width.toPx() }
    val printPx = with(density) { PrintSize.toPx() }

    Box(
        Modifier
            .fillMaxWidth()
            .height(PrintSize + 80.dp)
            .drawWithContent {
                drawContent()
                // The swipe: a thick pink stroke that rises slightly, like the wordmark's.
                if (progress <= 0f) return@drawWithContent
                val thick = 40.dp.toPx()
                val startX = -thick
                val endX = size.width + thick
                val x = startX + (endX - startX) * progress
                val midY = size.height / 2
                val rise = 16.dp.toPx()
                fun y(px: Float) = midY + rise / 2 - rise * ((px - startX) / (endX - startX))
                // The stroke fades out once it has crossed, leaving the title clear.
                val fade = ((1f - progress) / 0.15f).coerceIn(0f, 1f)
                drawLine(
                    c.eraser.copy(alpha = fade),
                    Offset(startX, y(startX)),
                    Offset(x, y(x)),
                    thick,
                    StrokeCap.Round,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        prints.forEachIndexed { i, bmp ->
            val (rot, dx, dy) = layout[i]
            // How far the swipe has passed this print: 0 = untouched, 1 = gone.
            val printLeft = widthPx / 2 + with(density) { dx.dp.toPx() } - printPx / 2
            val sweepX = -printPx * 0.3f + (widthPx + printPx * 0.6f) * progress
            val t = ((sweepX - printLeft) / printPx).coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset(dx.dp, dy.dp)
                    .graphicsLayer {
                        rotationZ = rot
                        alpha = 1f - t
                        val s = 1f - 0.35f * t
                        scaleX = s
                        scaleY = s
                    }
                    .size(PrintSize)
                    .clip(PrintShape)
                    .background(c.paper)
                    .border(1.dp, c.smudge, PrintShape)
                    .padding(4.dp),     // the Paper border of the print
            ) {
                Image(
                    bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@LocalizedScreenPreviews
@Composable
private fun DoneEndStatePreview() = PreviewFrame {
    DoneContent(
        title = stringResource(R.string.done_trash_title),
        body = pluralCount(R.plurals.done_trash_body, 12),
        prints = emptyList(),
        othersCount = 3,
        onReviewOthers = {},
        onFinish = {},
    )
}
