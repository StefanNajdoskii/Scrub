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
import androidx.activity.viewModels
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.scrub.app.ui.IntroPrefs
import com.scrub.app.ui.IntroScreen
import com.scrub.app.ui.rememberAnimationsOff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val authVm: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Launch screen: Chalk (or #1A1B1E in dark) with the icon, held until Firebase has
        // restored the session so the first frame is already the right screen.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { authVm.restoring.value }
        splash.setOnExitAnimationListener { provider ->
            val animationsOff = runCatching {
                Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }.getOrDefault(false)
            if (animationsOff) {
                provider.remove()
            } else {
                // Short fade into the first screen (same background color, so no flash).
                provider.view.animate()
                    .alpha(0f)
                    .setDuration(200L)
                    .withEndAction { provider.remove() }
                    .start()
            }
        }
        enableEdgeToEdge()
        setContent {
            ScrubTheme { AppRoot() }
        }
    }
}

/** Loads up to a few small bitmaps for the Done screen's print stack. Skips any that fail. */
private suspend fun loadPrints(context: Context, uris: List<Uri>): List<ImageBitmap> =
    withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(360)
                .allowHardware(false)
                .build()
            (context.imageLoader.execute(request) as? SuccessResult)
                ?.drawable?.toBitmap()?.asImageBitmap()
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
    val context = LocalContext.current
    // First-run introduction, shown once per device after the first sign-in.
    var introDone by remember { mutableStateOf(IntroPrefs.isDone(context)) }

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
                !introDone -> IntroScreen(onFinished = {
                    IntroPrefs.markDone(context)
                    introDone = true
                })
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
    val scope = rememberCoroutineScope()
    val animationsOff = rememberAnimationsOff()
    var permissionNote by remember { mutableStateOf<String?>(null) }
    // UI-only state: the photo being searched for faces, and thumbnails for the Done screen.
    var pickedPhoto by remember { mutableStateOf<Uri?>(null) }
    var donePrints by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }

    // Grab small copies of the photos about to be trashed or hidden, while the system dialog
    // is open: once confirmed, they're gone from the gallery.
    fun capturePrints() {
        val uris = vm.selectedMatches.take(6).map { it.image.uri }
        donePrints = emptyList()
        scope.launch { donePrints = loadPrints(context, uris) }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pickedPhoto = uri
            vm.onReferencePhotoPicked(uri)
        }
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
        Crossfade(
            targetState = vm.screen,
            animationSpec = if (animationsOff) snap() else tween(),
            label = "screen",
        ) { screen ->
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
                Screen.PickFace -> PickFaceScreen(vm, photo = pickedPhoto, onPickAnother = pickPhoto)
                Screen.Scanning -> ScanScreen(vm)
                Screen.Results -> ResultsScreen(
                    vm = vm,
                    onTrash = {
                        capturePrints()
                        vm.trashRequest()?.let {
                            trashLauncher.launch(IntentSenderRequest.Builder(it).build())
                        }
                    },
                    onVault = {
                        capturePrints()
                        vm.prepareVault { sender ->
                            vaultLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        }
                    },
                )
                Screen.Done -> DoneScreen(vm, donePrints)
                Screen.Vault -> VaultScreen(vm)
            }
        }

        vm.busyMessage?.let { BusyOverlay(it) }
    }
}
