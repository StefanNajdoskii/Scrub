package com.scrub.app.ui

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.scrub.app.ScrubViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultsScreen(vm: ScrubViewModel, onTrash: () -> Unit, onVault: () -> Unit) {
    val c = ScrubTheme.colors
    val visible = vm.visibleMatches
    val selectedCount = visible.count { it.image.id !in vm.deselected }
    var preview by remember { mutableStateOf<android.net.Uri?>(null) }
    val haptics = LocalHapticFeedback.current
    val off = rememberAnimationsOff()

    Column(Modifier.fillMaxSize()) {
        val actions: @Composable () -> Unit = {
            if (visible.isNotEmpty()) {
                val all = selectedCount == visible.size
                QuietButton(if (all) "Select none" else "Select all", onClick = { vm.selectAll(!all) })
            }
        }
        // The count rolls up or down as the strictness slider moves.
        when (visible.size) {
            0 -> TopBar("No matches", onBack = { vm.goHome() }, actions = actions)
            1 -> TopBar("1 photo of them", onBack = { vm.goHome() }, actions = actions)
            else -> TopBar(CountLabel("", visible.size, " photos of them"), onBack = { vm.goHome() }, actions = actions)
        }

        // Strictness
        Column(Modifier.padding(horizontal = Gutter)) {
            Spacer(Modifier.height(8.dp))
            val pct = ((vm.strictness - ScrubViewModel.MIN_SCORE) /
                (ScrubViewModel.MAX_SCORE - ScrubViewModel.MIN_SCORE) * 100).toInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Match strictness", style = ScrubType.SectionTitle, color = c.graphite, modifier = Modifier.weight(1f))
                Text("$pct%", style = ScrubType.Small, color = c.pencil)
            }
            Slider(
                value = vm.strictness,
                onValueChange = { vm.strictness = it },
                valueRange = ScrubViewModel.MIN_SCORE..ScrubViewModel.MAX_SCORE,
                modifier = Modifier.semantics { contentDescription = "Match strictness" },
                colors = SliderDefaults.colors(
                    thumbColor = c.graphite,
                    activeTrackColor = c.graphite,
                    inactiveTrackColor = c.smudge,
                    activeTickColor = c.graphite,
                    inactiveTickColor = c.smudge,
                ),
            )
            Text(
                "Seeing other people? Slide right. Missing photos of them? Slide left. " +
                    "Tap a photo to untick it, hold to see it larger.",
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
                    "All clear",
                    "No photos match at this strictness. Slide left to loosen it, or add more photos of them on the start screen.",
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = Gutter, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(visible, key = { it.image.id }) { match ->
                    val selected = match.image.id !in vm.deselected
                    PhotoTile(
                        model = match.image.uri,
                        contentDescription = if (selected) "Matching photo, ticked" else "Matching photo, not ticked",
                        selected = selected,
                        dimmed = !selected,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            vm.toggle(match)
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
            ActionBar(caption = CountLabel("", selectedCount, " of ${visible.size} ticked")) {
                SecondaryButton(
                    CountLabel("Hide ", selectedCount),
                    onClick = onVault,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    CountLabel("Move ", selectedCount, " to trash"),
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
                contentDescription = "Photo, larger",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TileRadius))
                    .background(c.paper)
                    .combinedClickable(onClickLabel = "Close", onClick = { preview = null }),
            )
        }
    }
}
