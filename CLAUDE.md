# Scrub

Android app (Kotlin, Jetpack Compose, minSdk 30) that finds every photo of one person on the
phone and trashes them or hides them in a private on-device vault.

## Privacy rules (non-negotiable)

- The app may use the internet **only for Firebase Authentication** (email/password and
  "Continue with Google" via Credential Manager), plus loading the signed-in user's Google
  profile photo for the avatar, and only when the user signs in. Guests make no Firebase Auth
  calls: `AuthViewModel` doesn't create `AuthRepository` (and so `FirebaseAuth`) for them.
- Photos, face fingerprints (`faces.bin`) and the vault must **never** be uploaded or synced.
  Face detection and recognition run on the device (ML Kit + bundled FaceNet).
- Do not add Firestore, Storage, Realtime Database, Analytics, Crashlytics, Remote Config,
  Performance, Messaging or any other Firebase/Google product or networked SDK.
- `auth/AuthRepository.kt` is the only code that talks to the network. Keep it that way.
- `android:allowBackup="false"` stays, so the vault and `faces.bin` aren't cloud-backed-up.

## Accounts

- Signing in is optional. "Continue without an account" on the sign-in screen sets a local
  guest flag (`AuthViewModel.isGuest`, prefs file `account`); guests go straight to Home on
  later launches and can use everything. The flag is cleared when they sign in. Signing out or
  deleting the account returns to the sign-in screen, not to guest mode.
- `AppRoot` in `MainActivity.kt` shows a wordmark while Firebase restores the session (never
  for guests), the auth screens (`ui/AuthScreens.kt`) when signed out and not a guest,
  otherwise the app. A guest's avatar opens `GuestAccountScreen` ("Not signed in").
- `auth/AuthViewModel.kt` owns auth UI state. `ScrubViewModel` (scanning, trash, vault) knows
  nothing about accounts; on sign out `AppRoot` only calls its existing `goHome()`/`startOver()`.
- Hidden photos stay on the device after sign out, and work the same for guests.
- Deleting the account deletes the Firebase user only, never photos or the vault.
- Library versions: Kotlin is 2.2.0, so Firebase BoM is pinned to 34.19.0 and googleid to
  1.2.0. Newer releases are built with Kotlin 2.4 metadata and fail to compile.

## UI

Use the pencil-and-eraser design system in `ui/Theme.kt` (colors via `ScrubTheme.colors`,
`ScrubType` text styles) and the components in `ui/Components.kt` / `ui/FormComponents.kt`.
Eraser pink is a fill, never text. Ink red is for errors and permanent actions only.
Every screen must work in light and dark; add `@ScreenPreviews` previews for new screens
(`@LocalizedScreenPreviews` adds Macedonian at 360dp, to catch text that doesn't fit).

## Languages and settings

- English (`values/`) and Macedonian (`values-mk/`, Cyrillic, informal "ти"). No hardcoded
  user-visible text: strings in `strings.xml`, counts in `plurals.xml` with the count as `%1$s`
  pre-formatted via `formatCount()` / `pluralCount()` / `countLabel()` (`ui/Text.kt`). Never
  build plurals with if/else or sentences by concatenation. ViewModels hand the UI `UiText`,
  not strings. "Scrub" and the "scrub" wordmark are never translated. Every new string needs
  its Macedonian version (follow the glossary at the top of `values-mk/strings.xml`).
- Bricolage has no Cyrillic. In Cyrillic locales headings/buttons use Onest (`res/font/
  onest_variable.ttf`, OFL); `ScrubType.ScreenTitle/SectionTitle/Button/StepNumber` pick the
  font, so read them only from composables. The wordmark is always Bricolage.
- Buttons and titles grow to 2 lines instead of cutting text off; keep it that way.
- `settings/AppSettings.kt` (DataStore, local only): Appearance (System/Light/Dark) and the app
  language. Android 13+ keeps the language itself (`locales_config.xml`, LocaleManager);
  Android 11-12 store it here and `MainActivity.attachBaseContext` overrides the locale (and
  on Android 11 the night mode). Android 12+ also gets `setApplicationNightMode`. The bundle
  keeps all languages (`bundle.language.enableSplit = false`) so the in-app choice always works.
  New languages: add `values-xx/`, `locales_config.xml` and `AppLanguage`.

## Build and release

- `./gradlew assembleDebug`. Needs `app/google-services.json` (Firebase project `scrub-ee420`).
- Release: `./gradlew bundleRelease`. R8 + resource shrinking are on; keep rules are in
  `app/proguard-rules.pro`. It also strips `Log.v/d/i` everywhere, so don't rely on those
  in release, and never log emails, user IDs or photo URIs.
- Signing reads `keystore.properties` (git-ignored, never commit it or any `*.jks`).
- Target/compile SDK 36. All 64-bit native libraries must stay **16 KB page aligned**
  (Play requirement). FaceNet runs on LiteRT 1.4.x (`com.google.ai.edge.litert:litert`), not
  `org.tensorflow:tensorflow-lite`, whose 2.16.1 library is 4 KB aligned. After adding or
  upgrading any library with native code, check alignment in the APK Analyzer or with
  `zipalign -c -P 16 -v 4 app.apk`.
- `EmbeddingRegressionTest` (androidTest) pins FaceNet output to the original runtime's
  values. Run it after touching the ML runtime or `FaceEmbedder`. `FacePipelineTest` covers
  straightening + flip averaging. `GalleryEvalTest` is a dev tool that only runs with
  `-e eval <tag>` and dumps gallery similarities to the app's private cache.
- Face pipeline changes that alter embeddings must bump `EmbeddingCache.VERSION` (faces get
  re-fingerprinted once). Quality rules live in `face/FaceQuality.kt` and apply at match time.
- The Match lab (`ui/MatchLabScreen.kt`, long-press the wordmark on Home) is debug-only: every
  entry point sits behind `BuildConfig.DEBUG` so R8 strips it. After touching it, check the
  release `mapping.txt` has no `MatchLab` classes.
- `connectedAndroidTest` uninstalls the app afterwards, which signs out and wipes `faces.bin`.
- Play submission steps: `PLAY_STORE_CHECKLIST.md`.
