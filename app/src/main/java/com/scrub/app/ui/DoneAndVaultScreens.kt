package com.scrub.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scrub.app.Screen
import com.scrub.app.ScrubViewModel

@Composable
fun DoneScreen(vm: ScrubViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Hero(Icons.Filled.Done, vm.doneTitle, vm.doneBody)
        Spacer(Modifier.weight(1f))
        if (vm.matches.isNotEmpty()) {
            SecondaryButton(
                "Review the remaining ${vm.visibleMatches.size}",
                onClick = { vm.screen = Screen.Results },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
        GradientButton("Finish", onClick = { vm.startOver() }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun VaultScreen(vm: ScrubViewModel) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val vaultList = vm.vaultItems
    val chosen = vaultList.filter { it.fileName in selected }

    Column(Modifier.fillMaxSize()) {
        TopBar("Vault", onBack = { vm.screen = Screen.Home }, actions = {
            if (vaultList.isNotEmpty()) {
                val all = selected.size == vaultList.size
                TextButton(onClick = {
                    selected = if (all) emptySet() else vaultList.map { it.fileName }.toSet()
                }) { Text(if (all) "Select none" else "Select all", color = ScrubColors.Coral) }
            }
        })
        Text(
            "Hidden from your gallery and other apps. Restore them any time. " +
                "Uninstalling Scrub permanently deletes everything in here.",
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (vaultList.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Hero(Icons.Filled.Lock, "The vault is empty", "Photos you hide after a scan will show up here.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(vaultList, key = { it.fileName }) { item ->
                    val isSel = item.fileName in selected
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selected = if (isSel) selected - item.fileName else selected + item.fileName
                            }
                    ) {
                        AsyncImage(
                            model = vm.vaultFile(item),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (isSel || selected.isEmpty()) 1f else 0.5f),
                        )
                        if (selected.isNotEmpty()) {
                            SelectionBadge(isSel, Modifier.align(Alignment.TopEnd).padding(6.dp))
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SecondaryButton(
                    "Delete",
                    onClick = { confirmDelete = true },
                    icon = Icons.Filled.DeleteForever,
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
                GradientButton(
                    if (chosen.isEmpty()) "Restore" else "Restore ${chosen.size}",
                    onClick = {
                        vm.restoreFromVault(chosen) { ok ->
                            Toast.makeText(context, "${ScrubViewModel.plural(ok)} back in your gallery", Toast.LENGTH_SHORT).show()
                        }
                        selected = emptySet()
                    },
                    icon = Icons.Filled.Restore,
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete forever?") },
            text = { Text("${ScrubViewModel.plural(chosen.size)} will be permanently erased. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFromVault(chosen)
                    selected = emptySet()
                    confirmDelete = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}
