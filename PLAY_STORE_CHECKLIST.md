# Scrub: Google Play release checklist

What's already done in the project:

- targetSdk / compileSdk 36. 16 KB page-size compliant: every 64-bit native library in the
  bundle has 16 KB-aligned LOAD segments, checked with an ELF check and `zipalign -c -P 16`.
- Release build: R8 + resource shrinking, release signing from `keystore.properties`.
- Version: versionCode 1, versionName 1.0.0.
- Account deletion in the app: Account screen → Delete account.

Everything below has to be done by you, in Play Console, Firebase or on the web.
Drafts are marked **DRAFT**. Read them and adjust before using them.

---

## 1. Upload key and signed bundle

1. Create the upload keystore **outside the project folder** (PowerShell or Git Bash). Use a
   strong password and keep a backup of the file and the passwords; you can't publish updates
   without them:

   ```
   keytool -genkeypair -v -keystore C:\Users\Stefan\keys\scrub-upload.jks -storetype PKCS12 -alias scrub-upload -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Create `keystore.properties` in the project root (it's git-ignored). Use forward slashes:

   ```
   storeFile=C:/Users/Stefan/keys/scrub-upload.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=scrub-upload
   keyPassword=YOUR_KEY_PASSWORD
   ```

3. Build the signed bundle:

   ```
   .\gradlew.bat bundleRelease
   ```

   Output: `app/build/outputs/bundle/release/app-release.aab`. The R8 mapping file is included
   in the bundle, so Play crash reports are readable. Check the signature with
   `keytool -printcert -jarfile app\build\outputs\bundle\release\app-release.aab`.

4. Print the upload key's fingerprints (needed for Firebase, step 3):

   ```
   keytool -list -v -keystore C:\Users\Stefan\keys\scrub-upload.jks -alias scrub-upload
   ```

## 2. Play Console account and app

- [ ] Developer account (one-time USD 25 fee, identity verification). Personal accounts also
      need the device verification in the Play Console app.
- [ ] Create the app: name "Scrub", default language, App, Free.
- [ ] Keep **Play App Signing** on (the default). You upload with your upload key and Google
      signs what users install.
- [ ] **Closed test first.** New personal developer accounts must run a closed test with
      **at least 12 testers opted in for 14 days in a row** before they can apply for
      production. Create a Google Group or email list, share the opt-in link, and make sure
      testers stay opted in for the full 14 days.
- [ ] **App access**: signing in is optional, and every feature (scan, review, trash, Hidden
      photos) works without an account via "Continue without an account". Still give reviewers
      a test account for the account features (sign in, sign out, delete account): create a
      dedicated email/password account in the app (e.g. `scrub.review@yourdomain`) and enter it
      under *App content → App access → All or some functionality is restricted*, with:
      "The whole app works without an account: on the first screen, tap 'Continue without an
      account'. To review the account features (Account screen, Delete account), sign in with
      the email and password below. To try the main feature, add a few photos of one person
      to the device gallery first."
- [ ] Ads: **No ads**.
- [ ] Target audience: recommended **18 and over** (or 16+). This keeps the app out of the
      Families programme; it's a photo-management tool, not made for children.
- [ ] Category: **Photography**.

## 3. Firebase

- [ ] Project settings → Your apps → com.scrub.app → **Add fingerprint**, for both:
  - the **upload key** SHA-1 and SHA-256 (from step 1.4), and
  - the **Play App Signing** SHA-1 and SHA-256 (Play Console → Test and release → Setup →
    App signing → "App signing key certificate").

  Google sign-in only works for builds signed with a key whose SHA-1 is registered. Builds
  from Play are signed with the App Signing key, so Google sign-in **fails for every Play
  user** until that one is added. Download `google-services.json` again afterwards and rebuild.
- [ ] Authentication → Sign-in method → Google: a **project support email** is set.
- [ ] Authentication → Templates: sender name "Scrub" and wording for the verification and
      password-reset emails.
- [ ] Authentication → Settings: keep **Email enumeration protection** on.
- [ ] Project settings → General: set the public-facing project name to "Scrub". The Google
      account picker shows it.

## 4. Photo and video permissions declaration

Scrub requests `READ_MEDIA_IMAGES`. Play only allows broad photo access when it's the app's
core function, so you must submit the declaration (App content → Photo and video permissions).

**DRAFT: core functionality description**

> Scrub's core and only purpose is to find every photo of one specific person in the user's
> gallery so the user can move them to the trash or hide them. To do this, Scrub must scan
> the entire photo library on the device: it runs on-device face detection and recognition
> on every image and compares each face with the person the user chose. The Android photo
> picker can't do this, because the user doesn't know which photos contain the person;
> finding them is the feature. The picker is used only for choosing the reference photo.
> All processing happens on the device; photos are never uploaded.

**DRAFT: short version** (if the form has a character limit)

> Scrub scans the user's whole photo library on-device to find every photo of one chosen
> person, then lets them trash or hide those photos. The picker can't support this because
> the user doesn't know which photos contain the person. Photos never leave the device.

Tip: a 30–60 second screen recording (add reference photo → scan → results) helps approval.
Upload it unlisted to YouTube and add the link if the form asks for one.

## 5. Privacy policy

Host it at a public URL (Firebase Hosting, GitHub Pages, Google Sites) and enter that URL
in Play Console (App content → Privacy policy) and in the store listing.
Fill in the bracketed parts.

**DRAFT**

> # Scrub privacy policy
>
> Effective date: [DATE]
>
> Scrub ("the app") helps you find photos of one person on your phone and move them to the
> trash or hide them. This policy explains what data the app uses and where it goes.
>
> ## Your photos and face data never leave your phone
>
> - Scrub reads the photos on your device only after you grant photo access.
> - Face detection and face recognition run entirely on your device. Scrub creates
>   numerical face "fingerprints" to compare faces. These fingerprints and the reference
>   photos you choose are stored only in the app's private storage on your phone.
> - Photos you hide are moved into the app's private storage on your phone.
> - None of this (photos, face fingerprints, or hidden photos) is ever uploaded, synced,
>   backed up to the cloud, or shared with us or anyone else.
> - Uninstalling Scrub deletes all of it from your phone, including hidden photos.
>
> ## Your account
>
> An account is optional: you can use all of Scrub without one. If you choose to sign in,
> accounts are provided by Firebase Authentication, a service of Google LLC. Depending on how
> you sign in, the following is sent to Firebase Authentication. If you don't sign in, nothing
> is sent:
>
> - **Email and password:** your name, email address and password. The password is stored
>   by Firebase in hashed form; we can't see it.
> - **Continue with Google:** your Google account's name, email address, profile photo
>   link and an account identifier, provided by Google when you choose your account.
>
> Firebase assigns your account a user ID. To keep sign-in secure and prevent abuse,
> Firebase Authentication also processes technical information such as your IP address and
> device information. See Google's privacy policy: https://policies.google.com/privacy and
> Firebase's privacy information: https://firebase.google.com/support/privacy.
>
> If you signed in with Google, the app downloads your Google profile photo to show it on
> the account button.
>
> We use your account data only to let you sign in. We don't use it for advertising and
> don't sell or share it.
>
> ## What Scrub does not do
>
> No ads. No analytics or tracking. No crash-reporting services. No selling or sharing of
> data.
>
> ## Deleting your account
>
> - In the app: Account → Delete account. This deletes your account immediately.
> - Without the app: follow the steps at [ACCOUNT DELETION PAGE URL].
>
> Deleting your account removes your name, email address and user ID from Firebase
> Authentication. It doesn't touch photos on your phone or your hidden photos. Those stay
> on your device until you remove them or uninstall the app.
>
> ## Children
>
> Scrub is not directed to children under 13 [or: under 16] and we don't knowingly collect
> their data.
>
> ## Changes
>
> If this policy changes, we'll update the effective date above.
>
> ## Contact
>
> [YOUR NAME OR COMPANY], [CONTACT EMAIL]

## 6. Account deletion web page

Play requires a web page where users can ask for account deletion **without reinstalling the
app**. Host it next to the privacy policy and enter the URL in the Data safety form.

**DRAFT**

> # Delete your Scrub account
>
> **In the app (fastest):** open Scrub → tap your account picture (top right) →
> **Delete account** → confirm. Your account is deleted immediately.
>
> **Without the app:** email **[CONTACT EMAIL]** from the email address of your Scrub account,
> subject "Delete my Scrub account". We'll delete it within 30 days and confirm by email.
>
> **What gets deleted:** your name, email address and user ID in Firebase Authentication.
> Nothing else is kept on our side.
>
> **What is not affected:** your photos. Scrub never uploads photos, face data or hidden
> photos; they exist only on your phone. To remove hidden photos, restore or delete them in
> the app, or uninstall Scrub (which deletes them permanently).

To handle email requests: Firebase console → Authentication → Users → find the email → ⋮ →
Delete account.

## 7. Data safety form

Answers that match the current app. **Check them against Firebase's own guidance** before
submitting: https://firebase.google.com/docs/android/play-data-disclosure (look at the
Firebase Authentication section; Firebase Auth includes reCAPTCHA bot protection, which may
add device-related data types).

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** |
| Do you provide a way for users to request that their data is deleted? | **Yes** (in-app + web URL from section 6) |

Account data is only collected if the user chooses to sign in; the whole app works without an
account. So mark every data type below as **Optional** ("users can choose whether this data is
collected"), not Required.

**Data types collected** (all: *Collected* yes, *Shared* **no**, *Processed ephemerally* no,
**Optional**, purpose **Account management**, plus **Fraud prevention, security, and
compliance** for user IDs):

| Category | Data type | Notes |
|---|---|---|
| Personal info | **Name** | Entered at sign-up or provided by Google |
| Personal info | **Email address** | |
| Personal info | **User IDs** | Firebase user ID |
| Device or other IDs | *Check Firebase's guidance* | Possibly needed for reCAPTCHA / abuse prevention |

**Not collected:** Photos and videos, location, contacts, app activity, app info and
performance, financial info, health, messages, audio, files.
Photos and face data are processed **only on the device** and never sent off it. Under Play's
definitions, on-device-only processing isn't "collection", so **don't** declare
Photos.

"Shared" stays **No**: Firebase acts as a service provider processing data on your behalf,
which Play doesn't count as sharing.

## 8. Content rating (IARC questionnaire)

- Category: **All other app types** (utility / productivity).
- Violence, fear, sexuality, language, controlled substances, gambling: **No** to all.
- Users can interact or exchange content with other users: **No**.
- Shares the user's current physical location: **No**.
- Allows digital purchases: **No**.
- Unrestricted internet access / web browser: **No**.

Expected result: Everyone / PEGI 3 / USK 0.

## 9. Store listing

**DRAFT: App name:** `Scrub: Remove Someone's Photos`  (30 chars max)

**DRAFT: Short description** (80 chars max):

> Scans your whole gallery for one person's face, then hides or trashes the photos.

**DRAFT: Full description:**

> Scrub finds every photo of one person in your gallery, so you can clear them out in
> minutes instead of scrolling for hours.
>
> HOW IT WORKS
> Scrub scans your entire photo library and uses face recognition to find each photo that
> shows the person you choose.
>
> 1. Add a photo of them. Pick a clear photo and tap their face. Add two or three different
>    photos for the best results.
> 2. Scan your photos. Scrub looks through every photo on your phone for their face.
> 3. Review and remove. Untick anything you want to keep, then move the rest to the trash or
>    hide them in Scrub's private vault.
>
> YOUR PHOTOS NEVER LEAVE YOUR PHONE
> All face detection and matching happens on your device. Your photos, face data and hidden
> photos are never uploaded, synced or shared. No account needed: the internet is used only
> if you choose to sign in.
>
> YOU STAY IN CONTROL
> • Nothing is removed until you confirm, and Android asks for your permission too.
> • Trashed photos go to your phone's trash, where you can recover them for about 30 days.
> • Hidden photos disappear from your gallery and other apps, and you can restore them any
>   time.
> • A strictness slider lets you fine-tune matches: fewer look-alikes, or catch every photo.
>
> GOOD TO KNOW
> • Scrub needs access to all your photos, because finding the person's photos means
>   checking every one. You can also allow selected photos only.
> • Uninstalling Scrub permanently deletes the hidden photos in its vault. Restore anything
>   you want to keep first.
> • Requires Android 11 or newer.

**Graphics** you need to make:

- [ ] App icon 512×512 PNG
- [ ] Feature graphic 1024×500
- [ ] At least 2 phone screenshots: Home, results, vault. Use photos you have rights to, or
      stock or AI-generated faces. Don't use real people without consent.

## 10. Before you submit: open points

- [ ] **Consider an in-app notice about face data before the first scan.** Scrub processes
      face data (biometric-like data) on the device. Play's User Data policy treats this as
      sensitive. The privacy policy covers it, and the Home footer and permission text say
      photos never leave the phone. A one-time notice right before the first scan ("Scrub
      creates face fingerprints on your phone to find matches. They never leave this
      device.") lowers the risk of a review rejection. Not added yet because this release
      was scoped to "no feature or design changes".
- [ ] Run the closed test (12 testers × 14 days), then apply for production.
- [ ] After the first upload to Play, add the Play App Signing SHA-1 to Firebase (section 3)
      and test "Continue with Google" with a build installed from Play.
