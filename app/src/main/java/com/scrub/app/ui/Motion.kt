package com.scrub.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

/*
 * Motion rules for Scrub:
 * - Every animation checks rememberAnimationsOff() (system "Remove animations"); when it's on,
 *   things jump straight to their end state.
 * - Decorative motion is limited to the wordmark swipe and the Done screen. Everything here
 *   is feedback: numbers changing, progress, loading.
 */

/** Rolls a number up or down (by direction of change) when it changes. */
@Composable
fun AnimatedCount(
    count: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val off = rememberAnimationsOff()
    AnimatedContent(
        targetState = count,
        transitionSpec = { rollTransition(initialState, targetState, off) },
        label = "count",
        modifier = modifier,
    ) { n ->
        Text("$n", style = style, color = color, maxLines = 1)
    }
}

/** "Before {count} after", with only the number rolling. Reads as one sentence to TalkBack. */
@Composable
fun CountText(
    before: String,
    count: Int,
    after: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier.clearAndSetSemantics { contentDescription = "$before$count$after" }) {
        if (before.isNotEmpty()) Text(before, style = style, color = color, maxLines = 1)
        AnimatedCount(count, style, color)
        if (after.isNotEmpty()) Text(after, style = style, color = color, maxLines = 1)
    }
}

/**
 * A large number that counts up through the values in between, each digit rolling as it
 * changes, e.g. the "photos checked" counter on the scan screen. Formatted with grouping
 * separators (4,812).
 */
@Composable
fun RollingNumber(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val off = rememberAnimationsOff()
    val shown by animateIntAsState(
        targetValue = value,
        animationSpec = if (off) tween(0) else tween(450, easing = LinearEasing),
        label = "rolling",
    )
    val text = NumberFormat.getIntegerInstance().format(shown)
    Row(modifier.clearAndSetSemantics { contentDescription = NumberFormat.getIntegerInstance().format(value) }) {
        // Key digits from the right so the ones digit keeps its slot as the number grows.
        text.forEachIndexed { i, ch ->
            val slot = text.length - i
            if (!ch.isDigit()) {
                Text("$ch", style = style, color = color)
            } else {
                androidx.compose.runtime.key(slot) {
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = { rollTransition(initialState.digitToInt(), targetState.digitToInt(), off, 140, digit = true) },
                        label = "digit",
                    ) { d -> Text("$d", style = style, color = color) }
                }
            }
        }
    }
}

/**
 * Slide up when the number grows, down when it shrinks. For a single digit rolling over
 * (9 -> 0 while counting up), [digit] keeps the direction "up".
 */
private fun rollTransition(from: Int, to: Int, off: Boolean, ms: Int = 220, digit: Boolean = false): ContentTransform {
    if (off || from == to) return EnterTransition.None togetherWith ExitTransition.None
    val up = to > from || (digit && from == 9 && to == 0)
    val dir = if (up) 1 else -1
    return ContentTransform(
        targetContentEnter = slideInVertically(tween(ms)) { h -> h * dir } + fadeIn(tween(ms)),
        initialContentExit = slideOutVertically(tween(ms)) { h -> -h * dir } + fadeOut(tween(ms)),
        sizeTransform = SizeTransform(clip = true),
    )
}

/**
 * Progress drawn like the wordmark swipe: a thick, slightly rising Eraser-pink stroke with
 * round ends over a Smudge track. [progress] is 0..1.
 */
@Composable
fun EraserProgress(progress: Float, modifier: Modifier = Modifier) {
    val c = ScrubTheme.colors
    val off = rememberAnimationsOff()
    val p by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (off) tween(0) else tween(400),
        label = "eraserProgress",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp),
    ) {
        val thick = 14.dp.toPx()
        val rise = 6.dp.toPx()           // rises a little from left to right, like the wordmark swipe
        val startX = thick / 2
        val endX = size.width - thick / 2
        val midY = size.height / 2
        fun y(x: Float) = midY + rise / 2 - rise * ((x - startX) / (endX - startX))
        drawLine(c.smudge, Offset(startX, y(startX)), Offset(endX, y(endX)), thick, StrokeCap.Round)
        if (p > 0f) {
            val x = startX + (endX - startX) * p
            drawLine(c.eraser, Offset(startX, y(startX)), Offset(x, y(x)), thick, StrokeCap.Round)
        }
    }
}

/**
 * Loading look for a photo: a thin Eraser-pink line sweeps slowly from top to bottom, over
 * and over. With animations off the line rests in the middle.
 */
fun Modifier.sweepLine(color: Color, progress: () -> Float): Modifier = drawWithContent {
    drawContent()
    val y = size.height * progress()
    drawLine(color, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
}

@Composable
fun rememberSweepProgress(): () -> Float {
    val off = rememberAnimationsOff()
    if (off) return { 0.5f }
    val t = rememberInfiniteTransition(label = "sweep")
    val v by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "sweepY",
    )
    return { v }
}
