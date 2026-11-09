package com.scrub.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun HomeScreen(
    vm: ScrubViewModel,
    onAddPhoto: () -> Unit,
    onScan: () -> Unit,
    permissionNote: String?,
    onPermissionNoteClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Wordmark()
        Text(
            "Clear someone out of your camera roll.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(36.dp))

        // Card: who to find
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, ScrubColors.Outline, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Text("Who do you want to find?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                if (vm.references.isEmpty())
                    "Pick a clear photo of them. You'll tap their face next."
                else
                    "Add 2–3 different photos of them for the most accurate results.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(vm.references) { ref ->
                    Box {
                        Image(
                            bitmap = ref.thumbnail.asImageBitmap(),
                            contentDescription = "Reference face",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .border(2.5.dp, ScrubColors.Accent, CircleShape),
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(ScrubColors.SurfaceHigh)
                                .clickable { vm.removeReference(ref) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, "Remove", Modifier.size(14.dp))
                        }
                    }
                }
                item {
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(ScrubColors.SurfaceHigh)
                            .clickable(onClick = onAddPhoto),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, "Add a photo of them", tint = ScrubColors.Coral)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        GradientButton(
            text = if (vm.references.isEmpty()) "Add a photo first" else "Scan my photos",
            icon = Icons.Filled.Search,
            onClick = onScan,
            enabled = vm.references.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (permissionNote != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                permissionNote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClick = onPermissionNoteClick),
            )
        }

        Spacer(Modifier.weight(1f))

        TextButton(onClick = { vm.screen = Screen.Vault }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Filled.Lock, null, Modifier.size(18.dp), tint = ScrubColors.Violet)
            Spacer(Modifier.width(8.dp))
            val n = vm.vaultItems.size
            Text(if (n == 0) "Vault" else "Vault · $n", color = MaterialTheme.colorScheme.onSurface)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Shield, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                "Works 100% on your phone. Nothing is ever uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
