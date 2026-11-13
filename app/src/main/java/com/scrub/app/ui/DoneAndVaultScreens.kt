package com.scrub.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scrub.app.R
import com.scrub.app.Screen
import com.scrub.app.ScrubViewModel
import java.text.NumberFormat

/** The vault, shown to people as "Hidden photos". */
@Composable
fun VaultScreen(vm: ScrubViewModel) {
    val c = ScrubTheme.colors
    val context = LocalContext.current
    // For the toast after restoring, shown from a callback outside composition.
    val number = NumberFormat.getIntegerInstance(currentLocale())
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val vaultList = vm.vaultItems
    val chosen = vaultList.filter { it.fileName in selected }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.hidden_photos), onBack = { vm.screen = Screen.Home }, actions = {
            if (vaultList.isNotEmpty()) {
                val all = selected.size == vaultList.size
                QuietButton(stringResource(if (all) R.string.action_select_none else R.string.action_select_all), onClick = {
                    selected = if (all) emptySet() else vaultList.map { it.fileName }.toSet()
                })
            }
        })
        Text(
            stringResource(R.string.vault_intro),
            modifier = Modifier.padding(horizontal = Gutter),
            style = ScrubType.Small,
            color = c.pencil,
        )
        Spacer(Modifier.height(16.dp))
        Hairline()

        if (vaultList.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(Gutter),
                contentAlignment = Alignment.Center,
            ) {
                Hero(
                    Icons.Outlined.VisibilityOff,
                    stringResource(R.string.vault_empty_title),
                    stringResource(R.string.vault_empty_body),
                )
            }
        } else {
            val photoTicked = stringResource(R.string.vault_photo_ticked)
            val photo = stringResource(R.string.vault_photo)
            val resources = context.resources
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = Gutter, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(vaultList, key = { it.fileName }) { item ->
                    val isSel = item.fileName in selected
                    val selecting = selected.isNotEmpty()
                    PhotoTile(
                        model = vm.vaultFile(item),
                        contentDescription = if (isSel) photoTicked else photo,
                        selected = if (selecting) isSel else null,
                        dimmed = selecting && !isSel,
                        onClick = {
                            selected = if (isSel) selected - item.fileName else selected + item.fileName
                        },
                    )
                }
            }

            ActionBar(caption = if (chosen.isEmpty()) stringResource(R.string.vault_tap_to_choose) else pluralCount(R.plurals.vault_ticked, chosen.size)) {
                DestructiveButton(
                    stringResource(R.string.action_delete_forever),
                    onClick = { confirmDelete = true },
                    enabled = chosen.isNotEmpty(),
                )
                PrimaryButton(
                    stringResource(R.string.vault_restore),
                    onClick = {
                        vm.restoreFromVault(chosen) { ok ->
                            val text = resources.getQuantityString(R.plurals.vault_restored, ok, number.format(ok))
                            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        }
                        selected = emptySet()
                    },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.vault_delete_title), style = ScrubType.SectionTitle, color = c.graphite) },
            text = {
                Text(
                    pluralCount(R.plurals.vault_delete_body, chosen.size),
                    style = ScrubType.Body,
                    color = c.pencil,
                )
            },
            confirmButton = {
                DestructiveButton(stringResource(R.string.action_delete_forever), onClick = {
                    vm.deleteFromVault(chosen)
                    selected = emptySet()
                    confirmDelete = false
                })
            },
            dismissButton = { QuietButton(stringResource(R.string.action_cancel), onClick = { confirmDelete = false }) },
            shape = DialogShape,
            containerColor = c.paper,
            tonalElevation = 0.dp,
        )
    }
}
