package com.scrub.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FaceRetouchingOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scrub.app.Screen
import com.scrub.app.ScrubViewModel

/** [photo] is the picked photo, shown while faces are being found in it. */
@Composable
fun PickFaceScreen(vm: ScrubViewModel, photo: Uri?, onPickAnother: () -> Unit) {
    val c = ScrubTheme.colors
    Column(Modifier.fillMaxSize()) {
        TopBar("Which one is them?", onBack = { vm.screen = Screen.Home })

        when {
            vm.pickLoading -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Gutter, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (photo != null) {
                    // A thin pink line sweeps slowly down the photo while faces are detected.
                    val sweep = rememberSweepProgress()
                    AsyncImage(
                        model = photo,
                        contentDescription = "The photo you picked",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(TileRadius))
                            .sweepLine(c.eraser, sweep),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Looking for faces",
                    style = ScrubType.Body,
                    color = c.pencil,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            vm.candidateFaces.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(Gutter),
            ) {
                Spacer(Modifier.weight(1f))
                Hero(
                    Icons.Filled.FaceRetouchingOff,
                    "No faces found",
                    "Try a photo where their face is clear, well lit and facing the camera.",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.weight(1f))
                PrimaryButton("Choose another photo", onPickAnother)
            }

            else -> {
                Text(
                    "Tap their face.",
                    style = ScrubType.Body,
                    color = c.pencil,
                    modifier = Modifier.padding(horizontal = Gutter),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(Gutter),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    itemsIndexed(vm.candidateFaces) { i, face ->
                        val thumb = face.thumbnail ?: return@itemsIndexed
                        FaceThumb(
                            thumb,
                            contentDescription = "Face ${i + 1} of ${vm.candidateFaces.size}",
                            onClick = { vm.chooseReference(face) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}
