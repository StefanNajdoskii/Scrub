# R8 rules for the release build. Libraries ship most of their own rules (consumer rules);
# only what they don't cover is here.

# --- LiteRT (TensorFlow Lite runtime) ---------------------------------------------------------
# The native library looks up Java classes and methods by name over JNI, which R8 can't see.
-keep class org.tensorflow.lite.** { *; }
# Optional GPU / GMS delegates that Scrub doesn't use.
-dontwarn org.tensorflow.lite.gpu.**
-dontwarn org.tensorflow.lite.gms.**

# --- ML Kit face detection, Firebase Auth, Credential Manager, googleid -----------------------
# Covered by their consumer rules (credentials-play-services-auth keeps
# CredentialProviderPlayServicesImpl, which Credential Manager loads by reflection).

# --- No personal data in logs -----------------------------------------------------------------
# Strip verbose/debug/info logging from the app and every library in release builds, so nothing
# like an email, user ID or photo URI can reach logcat. Warnings and errors are kept.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep line numbers so Play Console crash reports are readable (mapping.txt is uploaded with the
# bundle); hide the original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
