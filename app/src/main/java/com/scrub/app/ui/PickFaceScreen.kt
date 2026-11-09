package com.scrub.app.ui

import androidx.compose.foundation.layout.Arrangement
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

@Composable
fun PickFaceScreen(vm: ScrubViewModel, onPickAnother: () -> Unit) {
    val c = ScrubTheme.colors
    Column(Modifier.fillMaxSize()) {
        TopBar("Which one is them?", onBack = { vm.screen = Screen.Home })

        when {
            vm.pickLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spinner()
                    Spacer(Modifier.height(16.dp))
                    Text("Looking for faces", style = ScrubType.Body, color = c.pencil)
                }
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
