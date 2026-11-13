package com.scrub.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import com.scrub.app.R
import com.scrub.app.Screen
import com.scrub.app.ScrubViewModel

/** [photo] is the picked photo, shown while faces are being found in it. */
@Composable
fun PickFaceScreen(vm: ScrubViewModel, photo: Uri?, onPickAnother: () -> Unit) {
    val c = ScrubTheme.colors
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.pick_title), onBack = { vm.screen = Screen.Home })

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
                        contentDescription = stringResource(R.string.pick_photo_description),
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
                    stringResource(R.string.pick_looking),
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
                    stringResource(R.string.pick_no_faces_title),
                    stringResource(R.string.pick_no_faces_body),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.action_choose_another_photo), onPickAnother)
            }

            else -> {
                Text(
                    stringResource(R.string.pick_tap_face),
                    style = ScrubType.Body,
                    color = c.pencil,
                    modifier = Modifier.padding(horizontal = Gutter),
                )
                val weak = vm.weakCandidate
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(Gutter),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    itemsIndexed(vm.candidateFaces) { i, face ->
                        val thumb = face.thumbnail ?: return@itemsIndexed
                        Box {
                            FaceThumb(
                                thumb,
                                contentDescription = stringResource(
                                    if (face === weak) R.string.pick_face_description_selected
                                    else R.string.pick_face_description,
                                    formatCount(i + 1),
                                    formatCount(vm.candidateFaces.size),
                                ),
                                onClick = { vm.tapCandidate(face) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                            )
                            if (face === weak) SelectionBadge(true, Modifier.align(Alignment.TopEnd))
                        }
                    }
                }
                if (weak != null) {
                    WeakFaceNote(
                        onUse = { vm.chooseReference(weak) },
                        onPickAnother = onPickAnother,
                        modifier = Modifier.padding(horizontal = Gutter),
                    )
                }
            }
        }
    }
}

/**
 * Shown under the faces when the tapped face is small, blurry or turned away. Matching still
 * works with it, just less reliably, so the user can go ahead or pick a clearer photo.
 */
@Composable
private fun WeakFaceNote(onUse: () -> Unit, onPickAnother: () -> Unit, modifier: Modifier = Modifier) {
    val c = ScrubTheme.colors
    Column(modifier.padding(bottom = 24.dp)) {
        Text(
            stringResource(R.string.pick_weak_face),
            style = ScrubType.Small,
            color = c.pencil,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.pick_use_face), onUse)
        Spacer(Modifier.height(8.dp))
        SecondaryButton(stringResource(R.string.action_choose_another_photo), onPickAnother)
    }
}

@ScreenPreviews
@Composable
private fun WeakFaceNotePreview() = PreviewFrame {
    WeakFaceNote(onUse = {}, onPickAnother = {}, modifier = Modifier.padding(Gutter))
}
