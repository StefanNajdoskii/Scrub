package com.scrub.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrub.app.ui.BusyOverlay
import com.scrub.app.ui.DoneScreen
import com.scrub.app.ui.HomeScreen
import com.scrub.app.ui.PickFaceScreen
import com.scrub.app.ui.ResultsScreen
import com.scrub.app.ui.ScanScreen
import com.scrub.app.ui.ScrubColors
import com.scrub.app.ui.ScrubTheme
import com.scrub.app.ui.VaultScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScrubTheme { ScrubApp() }
        }
    }
}

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun granted(ctx: Context, p: String) =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

private fun hasFullAccess(ctx: Context) = granted(
    ctx,
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE
)

private fun hasPartialAccess(ctx: Context) =
    Build.VERSION.SDK_INT >= 34 && granted(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

@Composable
fun ScrubApp(vm: ScrubViewModel = viewModel()) {
    val context = LocalContext.current
    var permissionNote by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onReferencePhotoPicked(uri)
    }
    val pickPhoto = {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        when {
            hasFullAccess(context) -> {
                permissionNote = null
                vm.startScan()
            }
            hasPartialAccess(context) -> {
                permissionNote = "Scrub can only see the photos you allowed. Tap here to allow all photos in Settings."
                vm.startScan()
            }
            else -> permissionNote =
                "Scrub needs access to your photos to find them (nothing leaves your phone). Tap here to open Settings."
        }
    }

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { vm.onTrashResult(it.resultCode == Activity.RESULT_OK) }

    val vaultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { vm.onVaultDeleteResult(it.resultCode == Activity.RESULT_OK) }

    BackHandler(enabled = vm.screen != Screen.Home) {
        when (vm.screen) {
            Screen.Done -> vm.startOver()
            else -> vm.goHome()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ScrubColors.Background)
            .systemBarsPadding()
    ) {
        Crossfade(targetState = vm.screen, label = "screen") { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(
                    vm = vm,
                    onAddPhoto = pickPhoto,
                    onScan = {
                        if (hasFullAccess(context)) {
                            permissionNote = null
                            vm.startScan()
                        } else {
                            permissionLauncher.launch(mediaPermissions())
                        }
                    },
                    permissionNote = permissionNote,
                    onPermissionNoteClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    },
                )
                Screen.PickFace -> PickFaceScreen(vm, onPickAnother = pickPhoto)
                Screen.Scanning -> ScanScreen(vm)
                Screen.Results -> ResultsScreen(
                    vm = vm,
                    onTrash = {
                        vm.trashRequest()?.let {
                            trashLauncher.launch(IntentSenderRequest.Builder(it).build())
                        }
                    },
                    onVault = {
                        vm.prepareVault { sender ->
                            vaultLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        }
                    },
                )
                Screen.Done -> DoneScreen(vm)
                Screen.Vault -> VaultScreen(vm)
            }
        }

        vm.busyMessage?.let { BusyOverlay(it) }
    }
}
