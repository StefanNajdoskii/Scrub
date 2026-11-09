package com.scrub.app.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Remembers that the first-run introduction was seen, on this device. */
object IntroPrefs {
    private const val FILE = "intro"
    private const val KEY_DONE = "done"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
}

private class IntroPage(val title: String, val body: String, val art: @Composable (Modifier) -> Unit)

private val introPages = listOf(
    IntroPage("Pick their face", "Choose a photo of them and tap their face.") { PickFaceArt(it) },
    IntroPage("Scrub checks your photos", "It looks through your gallery on this phone. Nothing is uploaded.") {
        ScanArt(it)
    },
    IntroPage("You decide", "Review what it found. Nothing is removed until you confirm.") { DecideArt(it) },
)

/** Three numbered pages shown once after the first sign-in. */
@Composable
fun IntroScreen(onFinished: () -> Unit, initialPage: Int = 0) {
    val c = ScrubTheme.colors
    val pager = rememberPagerState(initialPage = initialPage) { introPages.size }
    val scope = rememberCoroutineScope()
    val off = rememberAnimationsOff()
    val last = pager.currentPage == introPages.lastIndex

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!last) QuietButton("Skip", onClick = onFinished)
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            IntroPageContent(index, introPages[index])
        }

        PageDots(pager, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = if (last) "Get started" else "Next",
            onClick = {
                if (last) onFinished()
                else scope.launch {
                    val next = pager.currentPage + 1
                    if (off) pager.scrollToPage(next) else pager.animateScrollToPage(next)
                }
            },
            modifier = Modifier.padding(horizontal = Gutter),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun IntroPageContent(index: Int, page: IntroPage) {
    val c = ScrubTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        page.art(
            Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth()
                .aspectRatio(1.25f)
        )
        Spacer(Modifier.height(40.dp))
        // The pages are a sequence, so they're numbered like the steps on Home.
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(c.graphite),
            contentAlignment = Alignment.Center,
        ) {
            Text("${index + 1}", style = ScrubType.StepNumber, color = c.chalk)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            page.title,
            style = ScrubType.ScreenTitle,
            color = c.graphite,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                contentDescription = "Step ${index + 1} of ${introPages.size}: ${page.title}"
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            page.body,
            style = ScrubType.Body,
            color = c.pencil,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun PageDots(pager: PagerState, modifier: Modifier = Modifier) {
    val c = ScrubTheme.colors
    val off = rememberAnimationsOff()
    Row(
        modifier.semantics {
            contentDescription = "Page ${pager.currentPage + 1} of ${pager.pageCount}"
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(pager.pageCount) { i ->
            val active = i == pager.currentPage
            val width by animateDpAsState(if (active) 24.dp else 8.dp, if (off) snap() else tween(200), label = "dotW")
            val color by animateColorAsState(if (active) c.graphite else c.smudge, if (off) snap() else tween(200), label = "dotC")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Line illustrations: Graphite 2dp strokes on Chalk, one Eraser-pink accent each
// ---------------------------------------------------------------------------------------------

private fun DrawScope.line() = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

/** A photo print: rounded frame with a head and shoulders inside. */
private fun DrawScope.print(topLeft: Offset, size: Size, ink: Color, background: Color, face: Boolean = true) {
    val r = CornerRadius(6.dp.toPx())
    drawRoundRect(background, topLeft, size, r)
    drawRoundRect(ink, topLeft, size, r, style = line())
    if (!face) return
    val cx = topLeft.x + size.width / 2
    val headR = size.minDimension * 0.17f
    val headY = topLeft.y + size.height * 0.42f
    drawCircle(ink, headR, Offset(cx, headY), style = line())
    val shoulders = Path().apply {
        val w = size.width * 0.27f
        val bottom = topLeft.y + size.height - 2.dp.toPx()
        moveTo(cx - w, bottom)
        quadraticTo(cx - w, headY + headR * 1.5f, cx, headY + headR * 1.5f)
        quadraticTo(cx + w, headY + headR * 1.5f, cx + w, bottom)
    }
    drawPath(shoulders, ink, style = line())
}

/** Page 1: one photo, their face circled in pink (the tap). */
@Composable
private fun PickFaceArt(modifier: Modifier) {
    val c = ScrubTheme.colors
    Canvas(modifier) {
        val w = size.height * 0.95f
        val frame = Size(w, size.height * 0.95f)
        val tl = Offset((size.width - frame.width) / 2, (size.height - frame.height) / 2)
        print(tl, frame, c.graphite, c.paper)
        // Accent: the tap ring around the face
        val cx = tl.x + frame.width / 2
        val headY = tl.y + frame.height * 0.42f
        drawCircle(c.eraser, frame.minDimension * 0.27f, Offset(cx, headY), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Page 2: a stack of photos with a pink scanning line across the top one. */
@Composable
private fun ScanArt(modifier: Modifier) {
    val c = ScrubTheme.colors
    Canvas(modifier) {
        val frame = Size(size.height * 0.72f, size.height * 0.72f)
        val center = Offset(size.width / 2, size.height / 2)
        // Back prints, slightly rotated, then the front one
        listOf(-8f to Offset(-28.dp.toPx(), -6.dp.toPx()), 6f to Offset(26.dp.toPx(), -10.dp.toPx())).forEach { (deg, shift) ->
            rotate(deg, center + shift) {
                print(center + shift - Offset(frame.width / 2, frame.height / 2), frame, c.graphite, c.paper)
            }
        }
        val tl = center + Offset(0f, 8.dp.toPx()) - Offset(frame.width / 2, frame.height / 2)
        print(tl, frame, c.graphite, c.paper)
        // Accent: the scanning line, overhanging the print a little
        val y = tl.y + frame.height * 0.36f
        drawLine(
            c.eraser,
            Offset(tl.x - 12.dp.toPx(), y),
            Offset(tl.x + frame.width + 12.dp.toPx(), y),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Page 3: a grid of photos, some ticked, with a pink swipe under the ones that will go. */
@Composable
private fun DecideArt(modifier: Modifier) {
    val c = ScrubTheme.colors
    Canvas(modifier) {
        val gap = 10.dp.toPx()
        val cols = 3
        val rows = 2
        val cell = minOf((size.width - gap * (cols - 1)) / cols, (size.height - gap * rows - 16.dp.toPx()) / rows)
        val gridW = cell * cols + gap * (cols - 1)
        val left = (size.width - gridW) / 2
        val top = 0f
        val ticked = setOf(0, 2, 4)
        for (i in 0 until cols * rows) {
            val x = left + (i % cols) * (cell + gap)
            val y = top + (i / cols) * (cell + gap)
            print(Offset(x, y), Size(cell, cell), c.graphite, c.paper, face = false)
            if (i in ticked) {
                // Tick badge in the corner
                val bc = Offset(x + cell - 12.dp.toPx(), y + 12.dp.toPx())
                drawCircle(c.graphite, 8.dp.toPx(), bc)
                val tick = Path().apply {
                    moveTo(bc.x - 3.5f.dp.toPx(), bc.y)
                    lineTo(bc.x - 1.dp.toPx(), bc.y + 2.5f.dp.toPx())
                    lineTo(bc.x + 3.5f.dp.toPx(), bc.y - 2.5f.dp.toPx())
                }
                drawPath(tick, c.chalk, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                // Simple face lines in the others
                drawCircle(c.graphite, cell * 0.14f, Offset(x + cell / 2, y + cell * 0.45f), style = line())
            }
        }
        // Accent: the eraser swipe under the grid, rising slightly like the wordmark
        val y = top + rows * cell + (rows - 1) * gap + 14.dp.toPx()
        drawLine(
            c.eraser,
            Offset(left + 8.dp.toPx(), y + 3.dp.toPx()),
            Offset(left + gridW - 8.dp.toPx(), y - 3.dp.toPx()),
            strokeWidth = 8.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@ScreenPreviews
@androidx.compose.runtime.Composable
private fun IntroPage1Preview() = PreviewFrame { IntroScreen(onFinished = {}, initialPage = 0) }

@ScreenPreviews
@Composable
private fun IntroPage2Preview() = PreviewFrame { IntroScreen(onFinished = {}, initialPage = 1) }

@ScreenPreviews
@Composable
private fun IntroPage3Preview() = PreviewFrame { IntroScreen(onFinished = {}, initialPage = 2) }
