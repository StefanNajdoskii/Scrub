package com.scrub.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FaceRetouchingOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.scrub.app.Screen
import com.scrub.app.ScrubViewModel

@Composable
fun PickFaceScreen(vm: ScrubViewModel, onPickAnother: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Which one is them?", onBack = { vm.screen = Screen.Home })

        when {
            vm.pickLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ScrubColors.Coral)
                    Spacer(Modifier.height(16.dp))
                    Text("Looking for faces…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            vm.candidateFaces.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Hero(
                    Icons.Filled.FaceRetouchingOff,
                    "No faces found",
                    "Try a photo where their face is clear, well lit and facing the camera.",
                )
                Spacer(Modifier.height(28.dp))
                GradientButton("Choose another photo", onPickAnother, icon = Icons.Filled.PhotoLibrary)
            }

            else -> {
                Text(
                    "Tap their face.",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(vm.candidateFaces) { face ->
                        val thumb = face.thumbnail ?: return@items
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = "Face",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .border(2.dp, ScrubColors.Outline, CircleShape)
                                .clickable { vm.chooseReference(face) },
                        )
                    }
                }
            }
        }
    }
}
