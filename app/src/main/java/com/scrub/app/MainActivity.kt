package com.scrub.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.ActivityNotFoundException
import androidx.core.graphics.drawable.toDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
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
import com.scrub.app.settings.AppSettings
import com.scrub.app.settings.ThemeMode
import com.scrub.app.ui.AppSettingsUi
import com.scrub.app.ui.DarkPalette
import com.scrub.app.ui.IntroPrefs
import com.scrub.app.ui.LightPalette
import com.scrub.app.ui.resolve
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
import com.scrub.app.ui.GuestAccountScreen
import com.scrub.app.ui.StartupScreen
import com.scrub.app.ui.findActivity
import com.scrub.app.ui.BusyOverlay
import com.scrub.app.ui.DoneScreen
import com.scrub.app.ui.HomeScreen
import com.scrub.app.ui.MatchLabScreen
import com.scrub.app.ui.PickFaceScreen
import com.scrub.app.ui.ResultsScreen
import com.scrub.app.ui.ScanScreen
import com.scrub.app.ui.ScrubTheme
import com.scrub.app.ui.VaultScreen

class MainActivity : ComponentActivity() {
    private val authVm: AuthViewModel by viewModels()

    /**
     * Android 11 has no per-app night mode and Android 11-12 no per-app language, so the chosen
     * Appearance / language are applied to this Activity's own configuration before anything
     * (the launch splash included) reads its resources.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        AppSettings.overrideConfiguration(newBase)?.let { applyOverrideConfiguration(it) }
    }

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
        val startTheme = AppSettings.themeModeNow(this)
        // On Android 12+ the system also starts the app (splash included) in the chosen mode.
        // Re-sync in case the system lost it (e.g. after a restore); it's a no-op otherwise.
        if (startTheme != ThemeMode.System && isDark(startTheme, systemInDark()) != resources.configuration.isNightModeActive) {
            AppSettings.applyNightMode(this, startTheme)
        }
        applySystemBars(isDark(startTheme, systemInDark()))
        val language = AppSettings.language(this)
        val systemLanguage = AppSettings.systemLanguageSettings(this)

        setContent {
            val theme by remember { AppSettings.themeMode(applicationContext) }
                .collectAsStateWithLifecycle(startTheme)
            val dark = isDark(theme, systemInDarkTheme())
            LaunchedEffect(dark) { applySystemBars(dark) }
            val settings = remember(theme) {
                AppSettingsUi(
                    theme = theme,
                    language = language,
                    onThemeChange = { mode ->
                        lifecycleScope.launch { AppSettings.setThemeMode(applicationContext, mode) }
                    },
                    openSystemLanguage = systemLanguage?.let { intent ->
                        {
                            try {
                                startActivity(intent)
                                true
                            } catch (e: ActivityNotFoundException) {
                                false
                            }
                        }
                    },
                    onLanguageChange = { choice ->
                        lifecycleScope.launch {
                            AppSettings.setLanguage(applicationContext, choice)
                            // Android 13+ recreates the screen itself after a language change.
                            if (Build.VERSION.SDK_INT < 33) recreate()
                        }
                    },
                )
            }
            ScrubTheme(dark = dark, animateChanges = true) { AppRoot(settings) }
        }
    }

    /**
     * Status and navigation bar icons follow the app's mode (dark icons on Light, light on Dark),
     * and so does the window behind Compose (seen e.g. while the keyboard animates).
     */
    private fun applySystemBars(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            // Same scrims as enableEdgeToEdge()'s defaults (only drawn with 3-button navigation)
            navigationBarStyle = SystemBarStyle.auto(LightNavScrim, DarkNavScrim) { dark },
        )
        val background = if (dark) DarkPalette.chalk else LightPalette.chalk
        window.setBackgroundDrawable(background.toArgb().toDrawable())
    }

    /** The phone's own dark mode setting, even while this Activity's configuration overrides it. */
    private fun systemInDark(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) resources.configuration.isNightModeActive
        else Resources.getSystem().configuration.isNightModeActive
}

private val LightNavScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

private fun isDark(theme: ThemeMode, systemDark: Boolean) = when (theme) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> systemDark
}

/**
 * The phone's dark mode setting, as Compose state. On Android 12+ the Activity configuration
 * follows it whenever Appearance is System. On Android 11 the configuration may be overridden
 * (see MainActivity.attachBaseContext), so read the system's own.
 */
@Composable
private fun systemInDarkTheme(): Boolean {
    if (Build.VERSION.SDK_INT >= 31) return isSystemInDarkTheme()
    val configuration = LocalConfiguration.current
    return remember(configuration) { Resources.getSystem().configuration.isNightModeActive }
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
 * Account gate. Signing in is optional. Shows the wordmark while Firebase restores a saved
 * session (never for guests), the auth screens when nobody is signed in and the user hasn't
 * chosen "Continue without an account", and the app otherwise. Guests and signed-in users get
 * the same app, Hidden photos included.
 */
@Composable
fun AppRoot(
    settings: AppSettingsUi,
    authVm: AuthViewModel = viewModel(),
    scrubVm: ScrubViewModel = viewModel(),
) {
    val restoring by authVm.restoring.collectAsStateWithLifecycle()
    val user by authVm.currentUser.collectAsStateWithLifecycle()
    var showAccount by rememberSaveable { mutableStateOf(false) }
    // A guest who tapped "Sign in or create account". The guest flag stays until they sign in.
    var guestSigningIn by rememberSaveable { mutableStateOf(false) }
    val isGuest = authVm.isGuest
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
        // A guest who just signed in keeps their session and lands on Home.
        if (uid != null && guestSigningIn) {
            guestSigningIn = false
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
                signedIn == null && isGuest && guestSigningIn -> AuthFlow(
                    authVm,
                    onBack = {
                        authVm.clearMessages()
                        guestSigningIn = false
                    },
                )
                signedIn == null && !isGuest -> AuthFlow(
                    authVm,
                    onContinueWithoutAccount = authVm::continueAsGuest,
                )
                !introDone -> IntroScreen(onFinished = {
                    IntroPrefs.markDone(context)
                    introDone = true
                })
                showAccount && signedIn == null -> {
                    BackHandler { showAccount = false }
                    GuestAccountScreen(
                        settings = settings,
                        onBack = { showAccount = false },
                        onSignIn = {
                            authVm.clearMessages()
                            guestSigningIn = true
                        },
                    )
                }
                showAccount && signedIn != null -> {
                    val activity = LocalContext.current.findActivity()
                    BackHandler { showAccount = false }
                    AccountScreen(
                        user = signedIn,
                        pending = authVm.pending,
                        error = authVm.error,
                        errorFrom = authVm.errorFrom,
                        needsReauth = authVm.needsReauth,
                        settings = settings,
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
                    onDismissVerifyNote = { signedIn?.let(authVm::dismissVerifyNote) },
                )
            }
        }
    }
}

@Composable
fun ScrubApp(
    vm: ScrubViewModel,
    /** Null for a guest. */
    user: AuthUser?,
    onAccount: () -> Unit,
    showVerifyNote: Boolean,
    onDismissVerifyNote: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animationsOff = rememberAnimationsOff()
    var permissionNote by remember { mutableStateOf<Int?>(null) }
    // UI-only state: the photo being searched for faces, and thumbnails for the Done screen.
    var pickedPhoto by remember { mutableStateOf<Uri?>(null) }
    var donePrints by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    // Debug builds only: the Match lab, opened by long-pressing the wordmark on Home.
    var showMatchLab by remember { mutableStateOf(false) }

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
                permissionNote = R.string.permission_partial
                vm.startScan()
            }
            else -> permissionNote = R.string.permission_denied
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

    if (BuildConfig.DEBUG && showMatchLab) {
        BackHandler { showMatchLab = false }
        MatchLabScreen(vm.labPhotos, vm.labReferences, onBack = { showMatchLab = false })
        return
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
                    onOpenMatchLab = if (BuildConfig.DEBUG) {
                        { showMatchLab = true }
                    } else null,
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

        vm.busyMessage?.let { BusyOverlay(it.resolve()) }
    }
}
