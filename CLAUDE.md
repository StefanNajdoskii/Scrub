# Scrub

Android app (Kotlin, Jetpack Compose, minSdk 30) that finds every photo of one person on the
phone and trashes them or hides them in a private on-device vault.

## Privacy rules (non-negotiable)

- The app may use the internet **only for Firebase Authentication** (email/password and
  "Continue with Google" via Credential Manager), plus loading the signed-in user's Google
  profile photo for the avatar.
- Photos, face fingerprints (`faces.bin`) and the vault must **never** be uploaded or synced.
  Face detection and recognition run on the device (ML Kit + bundled FaceNet).
- Do not add Firestore, Storage, Realtime Database, Analytics, Crashlytics, Remote Config,
  Performance, Messaging or any other Firebase/Google product or networked SDK.
- `auth/AuthRepository.kt` is the only code that talks to the network. Keep it that way.
- `android:allowBackup="false"` stays, so the vault and `faces.bin` aren't cloud-backed-up.

## Accounts

- Sign-in is required. `AppRoot` in `MainActivity.kt` shows a wordmark while Firebase restores
  the session, the auth screens (`ui/AuthScreens.kt`) when signed out, otherwise the app.
- `auth/AuthViewModel.kt` owns auth UI state. `ScrubViewModel` (scanning, trash, vault) knows
  nothing about accounts; on sign out `AppRoot` only calls its existing `goHome()`/`startOver()`.
- Hidden photos stay on the device after sign out and are only reachable while signed in.
- Deleting the account deletes the Firebase user only, never photos or the vault.
- Library versions: Kotlin is 2.2.0, so Firebase BoM is pinned to 34.19.0 and googleid to
  1.2.0. Newer releases are built with Kotlin 2.4 metadata and fail to compile.

## UI

Use the pencil-and-eraser design system in `ui/Theme.kt` (colors via `ScrubTheme.colors`,
`ScrubType` text styles) and the components in `ui/Components.kt` / `ui/FormComponents.kt`.
Eraser pink is a fill, never text. Ink red is for errors and permanent actions only.
Every screen must work in light and dark; add `@ScreenPreviews` previews for new screens.

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
  values. Run it after touching the ML runtime or `FaceEmbedder`.
- Play submission steps: `PLAY_STORE_CHECKLIST.md`.
