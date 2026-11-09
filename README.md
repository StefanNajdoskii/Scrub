# Scrub

Find every photo of one person on your phone, then trash them or hide them in a private vault.
Your photos never leave this phone. Face detection, matching and the vault all run on the device.
The only thing that goes online is signing in to your account (Firebase Authentication).

## Run it

1. Unzip, then in Android Studio choose **File → Open** and select the `Scrub` folder.
2. Wait for the Gradle sync to finish. The first sync downloads the dependencies.
   If Studio offers to upgrade the Android Gradle Plugin, accepting is fine.
3. Plug in an Android phone (Android 11 or newer) with USB debugging turned on,
   or start an emulator, then press **Run ▶**.

## How it works

| Step | What happens |
|---|---|
| Sign in | Create an account with email and password, or tap **Continue with Google**. Only your name, email and sign-in details go to Firebase Authentication. |
| Pick a photo | The system photo picker opens. ML Kit finds the faces in the photo and you tap theirs. Adding 2–3 photos improves accuracy. |
| Scan | Every gallery photo is decoded at up to 1024 px, faces are detected (ML Kit), and each face is turned into a 128-number fingerprint (FaceNet, `assets/facenet.tflite`). |
| Match | Each fingerprint is compared with the reference faces using cosine similarity. The **strictness** slider sets the cut-off (default 0.55). |
| Act | **Trash** uses Android's own trash (recoverable for about 30 days). **Vault** copies the photos into the app's private storage, then removes the originals. Both actions show Android's confirmation dialog first. |

Fingerprints are cached in `faces.bin`, so later scans only process new photos.

## Privacy

- Internet is used **only** for Firebase Authentication (and to show your Google profile photo).
- Photos, face fingerprints (`faces.bin`) and Hidden photos are never uploaded or synced.
  No Firestore, Storage, Analytics or Crashlytics.
- Signing out or deleting your account doesn't touch your photos or Hidden photos. Hidden photos
  stay on the phone and can be opened again once you sign in.

## Project layout

```
app/src/main/java/com/scrub/app/
  MainActivity.kt        app root: sign-in gate, permissions, system dialogs, navigation
  auth/AuthRepository.kt Firebase Authentication + Google sign-in (Credential Manager)
  auth/AuthViewModel.kt  account state for the sign-in and account screens
  ScrubViewModel.kt      state, scanning, trash/vault logic
  face/FaceEngine.kt     ML Kit detection + FaceNet embeddings
  data/Gallery.kt        MediaStore queries and image decoding
  data/EmbeddingCache.kt on-device fingerprint cache
  data/Vault.kt          private vault: copy in, restore, delete
  ui/                    Compose screens and theme
```

## Release

See `PLAY_STORE_CHECKLIST.md`. In short: create an upload keystore, add `keystore.properties`
in the project root (git-ignored), then run `gradlew bundleRelease`.

## Notes

- Firebase config lives in `app/google-services.json` (project `scrub-ee420`).
- Uninstalling Scrub deletes the vault. Restore anything you want to keep first.
- FaceNet runs on LiteRT (the TensorFlow Lite runtime, 16 KB page-size compatible).
- FaceNet model: from shubham0204/FaceRecognition_With_FaceNet_Android (Apache-2.0).
  The license is in `assets/facenet_LICENSE.txt`.
