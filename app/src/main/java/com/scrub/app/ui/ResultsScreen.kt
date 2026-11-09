package com.scrub.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.scrub.app.ScrubViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultsScreen(vm: ScrubViewModel, onTrash: () -> Unit, onVault: () -> Unit) {
    val visible = vm.visibleMatches
    val selectedCount = visible.count { it.image.id !in vm.deselected }
    var preview by remember { mutableStateOf<android.net.Uri?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = when (visible.size) {
                0 -> "No matches"
                1 -> "1 photo found"
                else -> "${visible.size} photos found"
            },
            onBack = { vm.goHome() },
            actions = {
                if (visible.isNotEmpty()) {
                    val all = selectedCount == visible.size
                    TextButton(onClick = { vm.selectAll(!all) }) {
                        Text(if (all) "Select none" else "Select all", color = ScrubColors.Coral)
                    }
                }
            },
        )

        // Strictness slider
        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Match strictness", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${((vm.strictness - ScrubViewModel.MIN_SCORE) / (ScrubViewModel.MAX_SCORE - ScrubViewModel.MIN_SCORE) * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = vm.strictness,
                onValueChange = { vm.strictness = it },
                valueRange = ScrubViewModel.MIN_SCORE..ScrubViewModel.MAX_SCORE,
                colors = SliderDefaults.colors(
                    thumbColor = ScrubColors.Coral,
                    activeTrackColor = ScrubColors.Coral,
                    inactiveTrackColor = ScrubColors.SurfaceHigh,
                ),
            )
            Text(
                "Seeing other people? Slide right. Missing photos of them? Slide left. " +
                    "Tap a photo to untick it, hold to view it bigger.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        if (visible.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Hero(
                    Icons.Filled.SentimentSatisfied,
                    "All clear",
                    "No photos matched at this strictness. Slide left to loosen it, or add more photos of them on the start screen.",
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(visible, key = { it.image.id }) { match ->
                    val selected = match.image.id !in vm.deselected
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = { vm.toggle(match) },
                                onLongClick = { preview = match.image.uri },
                            )
                    ) {
                        AsyncImage(
                            model = match.image.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (selected) 1f else 0.4f),
                        )
                        SelectionBadge(
                            selected,
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                        )
                    }
                }
            }
        }

        // Action bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(
                "Vault",
                onClick = onVault,
                icon = Icons.Filled.Lock,
                enabled = selectedCount > 0,
                modifier = Modifier.weight(1f),
            )
            GradientButton(
                if (selectedCount == 0) "Trash" else "Trash $selectedCount",
                onClick = onTrash,
                icon = Icons.Filled.Delete,
                enabled = selectedCount > 0,
                modifier = Modifier.weight(1.4f),
            )
        }
    }

    preview?.let { uri ->
        Dialog(onDismissRequest = { preview = null }) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = { preview = null }),
            )
        }
    }
}
