package com.scrub.app.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.scrub.app.Match
import com.scrub.app.R
import com.scrub.app.ScrubViewModel
import com.scrub.app.data.GalleryImage

@Composable
fun ResultsScreen(vm: ScrubViewModel, onTrash: () -> Unit, onVault: () -> Unit) = ResultsContent(
    visible = vm.visibleMatches,
    deselected = vm.deselected,
    strictness = vm.strictness,
    onStrictnessChange = { vm.strictness = it },
    onToggle = vm::toggle,
    onSelectAll = vm::selectAll,
    onBack = { vm.goHome() },
    onTrash = onTrash,
    onVault = onVault,
)

/** Stateless Results, so it can be previewed without a ViewModel. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultsContent(
    visible: List<Match>,
    deselected: Set<Long>,
    strictness: Float,
    onStrictnessChange: (Float) -> Unit,
    onToggle: (Match) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onBack: () -> Unit,
    onTrash: () -> Unit,
    onVault: () -> Unit,
) {
    val c = ScrubTheme.colors
    val selectedCount = visible.count { it.image.id !in deselected }
    var preview by remember { mutableStateOf<Uri?>(null) }
    val haptics = LocalHapticFeedback.current
    val off = rememberAnimationsOff()

    Column(Modifier.fillMaxSize()) {
        val actions: @Composable () -> Unit = {
            if (visible.isNotEmpty()) {
                val all = selectedCount == visible.size
                QuietButton(
                    stringResource(if (all) R.string.action_select_none else R.string.action_select_all),
                    onClick = { onSelectAll(!all) },
                )
            }
        }
        // The count rolls up or down as the strictness slider moves.
        if (visible.isEmpty()) {
            TopBar(stringResource(R.string.results_no_matches), onBack = onBack, actions = actions)
        } else {
            TopBar(countLabel(R.plurals.results_title, visible.size), onBack = onBack, actions = actions)
        }

        // Strictness
        Column(Modifier.padding(horizontal = Gutter)) {
            Spacer(Modifier.height(8.dp))
            val fraction = (strictness - ScrubViewModel.MIN_SCORE) /
                (ScrubViewModel.MAX_SCORE - ScrubViewModel.MIN_SCORE)
            // Whole percent, rounded down like before
            val pct = formatPercent((fraction * 100).toInt() / 100f)
            val strictnessLabel = stringResource(R.string.results_strictness)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strictnessLabel, style = ScrubType.SectionTitle, color = c.graphite, modifier = Modifier.weight(1f))
                Text(pct, style = ScrubType.Small, color = c.pencil)
            }
            Slider(
                value = strictness,
                onValueChange = onStrictnessChange,
                valueRange = ScrubViewModel.MIN_SCORE..ScrubViewModel.MAX_SCORE,
                modifier = Modifier.semantics { contentDescription = strictnessLabel },
                colors = SliderDefaults.colors(
                    thumbColor = c.graphite,
                    activeTrackColor = c.graphite,
                    inactiveTrackColor = c.smudge,
                    activeTickColor = c.graphite,
                    inactiveTickColor = c.smudge,
                ),
            )
            Text(
                stringResource(R.string.results_strictness_help),
                style = ScrubType.Small,
                color = c.pencil,
            )
            Spacer(Modifier.height(16.dp))
        }
        Hairline()

        if (visible.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(Gutter),
                contentAlignment = Alignment.Center,
            ) {
                Hero(
                    Icons.Filled.SentimentSatisfied,
                    stringResource(R.string.results_all_clear_title),
                    stringResource(R.string.results_all_clear_body),
                )
            }
        } else {
            val ticked = stringResource(R.string.results_photo_ticked)
            val unticked = stringResource(R.string.results_photo_unticked)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = Gutter, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(visible, key = { it.image.id }) { match ->
                    val selected = match.image.id !in deselected
                    PhotoTile(
                        model = match.image.uri,
                        contentDescription = if (selected) ticked else unticked,
                        selected = selected,
                        dimmed = !selected,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggle(match)
                        },
                        onLongClick = { preview = match.image.uri },
                    )
                }
            }
        }

        // Slides in when something gets ticked, out when nothing is.
        AnimatedVisibility(
            visible = selectedCount > 0,
            enter = if (off) EnterTransition.None
            else slideInVertically(tween(220)) { it } + expandVertically(tween(220), expandFrom = Alignment.Top),
            exit = if (off) ExitTransition.None
            else slideOutVertically(tween(180)) { it } + shrinkVertically(tween(180), shrinkTowards = Alignment.Top),
        ) {
            ActionBar(caption = countLabel(R.plurals.results_ticked_of, selectedCount, formatCount(visible.size))) {
                SecondaryButton(
                    countLabel(R.plurals.results_hide, selectedCount),
                    onClick = onVault,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    countLabel(R.plurals.results_trash, selectedCount),
                    onClick = onTrash,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1.5f),
                )
            }
        }
    }

    preview?.let { uri ->
        Dialog(onDismissRequest = { preview = null }) {
            AsyncImage(
                model = uri,
                contentDescription = stringResource(R.string.photo_larger),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TileRadius))
                    .background(c.paper)
                    .combinedClickable(onClickLabel = stringResource(R.string.action_close), onClick = { preview = null }),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private val previewMatches = (1L..12L).map { id ->
    Match(GalleryImage(id, Uri.EMPTY, 0L, "photo_$id.jpg", "Pictures/", "image/jpeg", 0L), 0.8f)
}

/** 11 of 12 ticked, so the bottom bar shows with its longest labels. */
@LocalizedScreenPreviews
@Composable
private fun ResultsPreview() = PreviewFrame {
    ResultsContent(
        visible = previewMatches, deselected = setOf(3L), strictness = ScrubViewModel.DEFAULT_STRICTNESS,
        onStrictnessChange = {}, onToggle = {}, onSelectAll = {}, onBack = {}, onTrash = {}, onVault = {},
    )
}

/** One match: singular forms. */
@LocalizedScreenPreviews
@Composable
private fun ResultsOnePreview() = PreviewFrame {
    ResultsContent(
        visible = previewMatches.take(1), deselected = emptySet(), strictness = 0.7f,
        onStrictnessChange = {}, onToggle = {}, onSelectAll = {}, onBack = {}, onTrash = {}, onVault = {},
    )
}

@ScreenPreviews
@Composable
private fun ResultsEmptyPreview() = PreviewFrame {
    ResultsContent(
        visible = emptyList(), deselected = emptySet(), strictness = ScrubViewModel.MAX_SCORE,
        onStrictnessChange = {}, onToggle = {}, onSelectAll = {}, onBack = {}, onTrash = {}, onVault = {},
    )
}
