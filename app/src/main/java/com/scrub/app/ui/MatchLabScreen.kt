package com.scrub.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.scrub.app.ScannedPhoto
import com.scrub.app.ScrubViewModel
import com.scrub.app.data.MatchLabLabels
import com.scrub.app.face.FaceQuality
import com.scrub.app.face.MatchEval
import com.scrub.app.face.MatchStrategy
import com.scrub.app.face.PhotoScores
import com.scrub.app.face.ReferenceSet
import com.scrub.app.face.ThresholdStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Match lab: a developer screen for tuning matching on real photos. Only reachable in debug
 * builds (long-press the wordmark on Home); every entry point is behind BuildConfig.DEBUG, so
 * R8 removes this file's code from release builds.
 */

/** One row of the lab: a photo and its scores. [model] is what Coil loads for the thumbnail. */
class LabItem(val id: Long, val model: Any, val scores: PhotoScores)

@Composable
fun MatchLabScreen(photos: List<ScannedPhoto>, references: ReferenceSet?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { MatchLabLabels(File(context.filesDir, "matchlab_labels.json")) }
    val labels = remember { mutableStateMapOf<Long, Boolean>() }
    LaunchedEffect(store) { labels.putAll(withContext(Dispatchers.IO) { store.load() }) }

    val items by produceState<List<LabItem>?>(null, photos, references) {
        value = if (references == null) emptyList() else withContext(Dispatchers.Default) {
            photos.mapNotNull { p -> PhotoScores.of(p.faces, references)?.let { LabItem(p.image.id, p.image.uri, it) } }
                .sortedByDescending { it.scores.bestScore }
        }
    }

    MatchLabContent(
        items = items,
        labels = labels,
        referenceCount = references?.size ?: 0,
        onLabel = { id, them ->
            if (labels[id] == them) labels.remove(id) else labels[id] = them
            val snapshot = labels.toMap()
            scope.launch(Dispatchers.IO) { store.save(snapshot) }
        },
        onCopy = { text -> copyToClipboard(context, text) },
        onBack = onBack,
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Match lab results", text))
    Toast.makeText(context, "Results copied", Toast.LENGTH_SHORT).show()
}

/** Stateless lab, so it can be previewed. [items] is null while scores are being computed. */
@Composable
private fun MatchLabContent(
    items: List<LabItem>?,
    labels: Map<Long, Boolean>,
    referenceCount: Int,
    onLabel: (Long, Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onBack: () -> Unit,
) {
    val defaultStrategy = ReferenceSet.defaultStrategy(referenceCount)
    val threshold = ScrubViewModel.DEFAULT_STRICTNESS
    var histogramStrategy by remember(referenceCount) { mutableStateOf(defaultStrategy) }
    val rows = items.orEmpty().map { it.scores to labels[it.id] }

    Column(Modifier.fillMaxSize()) {
        TopBar("Match lab", onBack = onBack, actions = {
            if (!items.isNullOrEmpty()) QuietButton("Copy results", onClick = {
                onCopy(MatchEval.summary(rows, referenceCount, defaultStrategy, threshold))
            })
        })

        when {
            items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(Gutter), contentAlignment = Alignment.Center) {
                Hero(
                    Icons.Outlined.Science,
                    "Nothing to label yet",
                    "Run a scan first. Every photo with a usable face shows up here.",
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = Gutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Summary(rows, referenceCount, defaultStrategy, threshold, histogramStrategy) {
                        histogramStrategy = it
                    }
                }
                item { Hairline() }
                items(items, key = { it.id }) { item ->
                    LabRow(item, labels[item.id], onLabel = { onLabel(item.id, it) })
                }
            }
        }
    }
}

@Composable
private fun Summary(
    rows: List<Pair<PhotoScores, Boolean?>>,
    referenceCount: Int,
    defaultStrategy: MatchStrategy,
    threshold: Float,
    histogramStrategy: MatchStrategy,
    onPickStrategy: (MatchStrategy) -> Unit,
) {
    val c = ScrubTheme.colors
    val mono = ScrubType.Small.copy(fontFamily = FontFamily.Monospace)
    val them = rows.count { it.second == true }
    val notThem = rows.count { it.second == false }
    Column {
        Text(
            "${rows.size} photos · $them them · $notThem not them · " +
                "$referenceCount reference${if (referenceCount == 1) "" else "s"} · default ${defaultStrategy.label}",
            style = ScrubType.Small,
            color = c.pencil,
        )
        Spacer(Modifier.height(16.dp))
        Text("Suggested thresholds (max F1)", style = ScrubType.SectionTitle, color = c.graphite)
        Spacer(Modifier.height(8.dp))
        Text("strategy  best  P    R    F1  │ at ${MatchEval.f2(threshold)} P    R    F1", style = mono, color = c.pencil)
        for (s in MatchStrategy.entries) {
            val samples = MatchEval.labeled(rows, s)
            val best = MatchEval.bestThreshold(samples)
            val at = MatchEval.statsAt(samples, threshold)
            val selected = s == histogramStrategy
            Text(
                s.label.padEnd(9) + " " + statsCells(best, withThreshold = true) + " │       " + statsCells(at, withThreshold = false),
                style = mono,
                color = if (selected) c.graphite else c.pencil,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Show ${s.label} histogram") { onPickStrategy(s) }
                    .padding(vertical = 6.dp),
            )
        }
        Text("Tap a strategy to see its histogram.", style = ScrubType.Small, color = c.pencil)
        Spacer(Modifier.height(16.dp))
        Text("Scores, ${histogramStrategy.label}", style = ScrubType.SectionTitle, color = c.graphite)
        Spacer(Modifier.height(8.dp))
        val samples = MatchEval.labeled(rows, histogramStrategy)
        Histogram(
            them = MatchEval.histogram(samples.filter { it.them }.map { it.score }),
            notThem = MatchEval.histogram(samples.filter { !it.them }.map { it.score }),
            defaultThreshold = threshold,
            suggested = MatchEval.bestThreshold(samples)?.threshold,
        )
    }
}

private fun statsCells(s: ThresholdStats?, withThreshold: Boolean): String {
    if (s == null) return "–     –    –    –  "
    val t = if (withThreshold) MatchEval.f2(s.threshold) + "  " else ""
    return t + "${MatchEval.f2(s.precision)} ${MatchEval.f2(s.recall)} ${MatchEval.f2(s.f1)}"
}

/**
 * Bars per 0.05 score bin: Graphite = "Them", Eraser = "Not them". Solid line at the default
 * threshold, dashed at the suggested one.
 */
@Composable
private fun Histogram(them: IntArray, notThem: IntArray, defaultThreshold: Float, suggested: Float?) {
    val c = ScrubTheme.colors
    val bins = them.size
    val top = maxOf(1, (them + notThem).maxOrNull() ?: 1)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(c.paper),
    ) {
        val binW = size.width / bins
        val barW = binW * 0.4f
        for (i in 0 until bins) {
            val x = i * binW + binW * 0.1f
            val hThem = size.height * them[i] / top
            val hNot = size.height * notThem[i] / top
            drawRect(c.graphite, Offset(x, size.height - hThem), Size(barW, hThem))
            drawRect(c.eraser, Offset(x + barW, size.height - hNot), Size(barW, hNot))
        }
        val dx = defaultThreshold * size.width
        drawLine(c.pencil, Offset(dx, 0f), Offset(dx, size.height), strokeWidth = 1.dp.toPx())
        if (suggested != null) {
            val sx = suggested.coerceIn(0f, 1f) * size.width
            drawLine(
                c.graphite, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text("0", style = ScrubType.Small, color = c.pencil, modifier = Modifier.weight(1f))
        Text("0.5", style = ScrubType.Small, color = c.pencil, modifier = Modifier.weight(1f))
        Text("1", style = ScrubType.Small, color = c.pencil)
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(c.graphite))
        Spacer(Modifier.width(6.dp))
        Text("Them", style = ScrubType.Small, color = c.graphite)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(12.dp).background(c.eraser))
        Spacer(Modifier.width(6.dp))
        Text("Not them", style = ScrubType.Small, color = c.graphite)
    }
    Text(
        "Solid line: default ${MatchEval.f2(defaultThreshold)}. Dashed: suggested" +
            (suggested?.let { " ${MatchEval.f2(it)}" } ?: ", once something is labelled Them") + ".",
        style = ScrubType.Small,
        color = c.pencil,
    )
}

@Composable
private fun LabRow(item: LabItem, label: Boolean?, onLabel: (Boolean) -> Unit) {
    val c = ScrubTheme.colors
    val p = item.scores
    val q = p.quality
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhotoThumb(item.model, Modifier.size(72.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${MatchEval.f2(p.bestScore)} · ${p.bestStrategy.label}",
                    style = ScrubType.SectionTitle,
                    color = c.graphite,
                )
                Text(
                    MatchStrategy.entries.joinToString(" · ") { "${it.label} ${MatchEval.f2(p.scores[it])}" },
                    style = ScrubType.Small,
                    color = c.pencil,
                )
                Text(
                    "size ${q.sizePx}px · yaw ${q.yaw.toInt()}° · roll ${q.roll.toInt()}° · " +
                        "sharp ${q.sharpness.toInt()}" + if (p.usableFaces > 1) " · ${p.usableFaces} faces" else "",
                    style = ScrubType.Small,
                    color = c.pencil,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabelButton("Them", label == true, { onLabel(true) }, Modifier.weight(1f))
            LabelButton("Not them", label == false, { onLabel(false) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LabelButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) =
    if (selected) PrimaryButton(text, onClick, modifier) else SecondaryButton(text, onClick, modifier)

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private fun previewItems(): List<LabItem> {
    val scores = listOf(0.82f, 0.74f, 0.61f, 0.52f, 0.44f, 0.31f, 0.18f)
    return scores.mapIndexed { i, s ->
        LabItem(
            id = i.toLong(),
            model = "",
            scores = PhotoScores(
                scores = mapOf(MatchStrategy.Max to s, MatchStrategy.Centroid to s - 0.03f, MatchStrategy.Hybrid to s - 0.015f),
                bestStrategy = MatchStrategy.Max,
                bestScore = s,
                quality = FaceQuality(sizePx = 140 - i * 10, yaw = -8f + i * 3, roll = 4f, sharpness = 320f - i * 30),
                usableFaces = 1,
            ),
        )
    }
}

@ScreenPreviews
@Composable
private fun MatchLabPreview() = PreviewFrame {
    MatchLabContent(
        items = previewItems(),
        labels = mapOf(0L to true, 1L to true, 2L to true, 3L to false, 5L to false, 6L to false),
        referenceCount = 2,
        onLabel = { _, _ -> },
        onCopy = {},
        onBack = {},
    )
}

@ScreenPreviews
@Composable
private fun MatchLabEmptyPreview() = PreviewFrame {
    MatchLabContent(items = emptyList(), labels = emptyMap(), referenceCount = 0, onLabel = { _, _ -> }, onCopy = {}, onBack = {})
}
