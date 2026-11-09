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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrub.app.auth.AuthUser
import com.scrub.app.auth.AuthViewModel
import com.scrub.app.ui.AccountScreen
import com.scrub.app.ui.AuthFlow
import com.scrub.app.ui.StartupScreen
import com.scrub.app.ui.findActivity
import com.scrub.app.ui.BusyOverlay
import com.scrub.app.ui.DoneScreen
import com.scrub.app.ui.HomeScreen
import com.scrub.app.ui.PickFaceScreen
import com.scrub.app.ui.ResultsScreen
import com.scrub.app.ui.ScanScreen
import com.scrub.app.ui.ScrubTheme
import com.scrub.app.ui.VaultScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScrubTheme { AppRoot() }
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

/**
 * Sign-in gate. Shows the wordmark while Firebase restores the saved session, the auth screens
 * when nobody is signed in, and the app otherwise. Hidden photos stay on the device after sign
 * out but can only be opened from inside the app, i.e. while signed in.
 */
@Composable
fun AppRoot(
    authVm: AuthViewModel = viewModel(),
    scrubVm: ScrubViewModel = viewModel(),
) {
    val restoring by authVm.restoring.collectAsStateWithLifecycle()
    val user by authVm.currentUser.collectAsStateWithLifecycle()
    var showAccount by rememberSaveable { mutableStateOf(false) }

    // When the account changes (sign out, delete, switch), drop the previous person's
    // in-memory session: picked faces, matches and the current screen.
    var lastUid by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(restoring, user?.uid) {
        if (restoring) return@LaunchedEffect
        val uid = user?.uid
        if (lastUid != null && uid != lastUid) {
            scrubVm.goHome()
            scrubVm.startOver()
            showAccount = false
        }
        lastUid = uid
    }

    // Root surface: sets the background and the default content color for every screen,
    // so text and icons never fall back to black.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            val signedIn = user
            when {
                restoring -> StartupScreen()
                signedIn == null -> AuthFlow(authVm)
                showAccount -> {
                    val activity = LocalContext.current.findActivity()
                    BackHandler { showAccount = false }
                    AccountScreen(
                        user = signedIn,
                        pending = authVm.pending,
                        error = authVm.error,
                        errorFrom = authVm.errorFrom,
                        needsReauth = authVm.needsReauth,
                        onBack = { showAccount = false },
                        onSignOut = authVm::signOut,
                        onDelete = authVm::deleteAccount,
                        onReauthWithPassword = authVm::reauthWithPassword,
                        onReauthWithGoogle = { activity?.let(authVm::reauthWithGoogle) },
                        onCancelReauth = authVm::cancelReauth,
                    )
                }
                else -> ScrubApp(
                    vm = scrubVm,
                    user = signedIn,
                    onAccount = {
                        authVm.clearMessages()
                        showAccount = true
                    },
                    showVerifyNote = authVm.showVerifyNote(signedIn),
                    onDismissVerifyNote = { authVm.dismissVerifyNote(signedIn) },
                )
            }
        }
    }
}

@Composable
fun ScrubApp(
    vm: ScrubViewModel,
    user: AuthUser,
    onAccount: () -> Unit,
    showVerifyNote: Boolean,
    onDismissVerifyNote: () -> Unit,
) {
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
                permissionNote = "Scrub can only see the photos you allowed. To scan everything, allow all photos in Settings."
                vm.startScan()
            }
            else -> permissionNote =
                "Scrub needs access to your photos to find them. Nothing leaves your phone."
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

    Box(Modifier.fillMaxSize()) {
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
                    user = user,
                    onAccount = onAccount,
                    showVerifyNote = showVerifyNote,
                    onDismissVerifyNote = onDismissVerifyNote,
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
