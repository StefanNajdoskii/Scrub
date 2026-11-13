package com.scrub.app.settings

import android.app.LocaleManager
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.Locale

/** The Appearance switch on the Account screen. */
enum class ThemeMode(val key: String) {
    System("system"), Light("light"), Dark("dark");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: System
    }
}

/**
 * Languages Scrub is translated into. [Phone] follows the phone's language (English for every
 * language without a translation). Keep in sync with res/xml/locales_config.xml.
 */
enum class AppLanguage(val tag: String?) {
    Phone(null), English("en"), Macedonian("mk");

    companion object {
        fun from(tag: String?): AppLanguage {
            if (tag.isNullOrEmpty()) return Phone
            val language = Locale.forLanguageTag(tag).language
            return entries.firstOrNull { it.tag == language } ?: Phone
        }
    }
}

private val Context.settingsStore by preferencesDataStore(name = "settings")

/**
 * Local app settings, kept in DataStore in the app's private storage (never backed up or
 * synced: allowBackup is off).
 *
 * - Appearance applies everywhere. On Android 12+ it's also handed to the system with
 *   UiModeManager.setApplicationNightMode, so the launch splash uses it too.
 * - The language is stored here only on Android 11-12. Android 13+ keeps the per-app
 *   language itself (Settings > Apps > Scrub > Language), read through LocaleManager.
 */
object AppSettings {
    private val THEME = stringPreferencesKey("theme")
    private val LANGUAGE = stringPreferencesKey("language")

    fun themeMode(context: Context): Flow<ThemeMode> =
        context.settingsStore.data.map { ThemeMode.from(it[THEME]) }

    /**
     * For startup only (attachBaseContext / onCreate), so the first frame already has the
     * right theme and language. Reads one tiny local file.
     */
    fun themeModeNow(context: Context): ThemeMode = runBlocking { themeMode(context).first() }

    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.settingsStore.edit { it[THEME] = mode.key }
        applyNightMode(context, mode)
    }

    /** Android 12+: the system then starts Scrub (splash included) in this mode. */
    fun applyNightMode(context: Context, mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < 31) return
        val uiModeManager = context.getSystemService(UiModeManager::class.java) ?: return
        uiModeManager.setApplicationNightMode(
            when (mode) {
                // AUTO = follow the system setting
                ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
            }
        )
    }

    /** The chosen language ([AppLanguage.Phone] when following the phone). */
    fun language(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            AppLanguage.from(locales?.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag())
        } else {
            runBlocking { AppLanguage.from(context.settingsStore.data.first()[LANGUAGE]) }
        }

    /**
     * Android 13+: the system's per-app language screen for Scrub; null on Android 11-12. A
     * phone that removed the screen throws ActivityNotFoundException: then Scrub shows its own
     * picker instead.
     */
    fun systemLanguageSettings(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 33) return null
        return Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }

    /**
     * Sets the app language from Scrub's own picker. Android 13+ hands it to the system (which
     * recreates the screen). On Android 11-12 it's stored here and the caller recreates the
     * Activity so [overrideConfiguration] picks it up.
     */
    suspend fun setLanguage(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            context.settingsStore.edit { it[LANGUAGE] = language.tag.orEmpty() }
        }
    }

    /**
     * What the Activity has to override in its own configuration, where the system can't do it:
     * the night mode on Android 11 (no per-app night mode before 12) and the language on
     * Android 11-12 (no per-app languages before 13). Null when nothing needs overriding.
     */
    fun overrideConfiguration(base: Context): Configuration? {
        if (Build.VERSION.SDK_INT >= 33) return null
        var override: Configuration? = null
        if (Build.VERSION.SDK_INT < 31) {
            val mode = themeModeNow(base)
            if (mode != ThemeMode.System) {
                val night = if (mode == ThemeMode.Dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                override = Configuration().apply {
                    uiMode = (base.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                }
            }
        }
        val tag = language(base).tag
        if (tag != null) {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            override = (override ?: Configuration()).apply { setLocales(LocaleList(locale)) }
        } else {
            // Back to the phone's language after an earlier choice in this process.
            Locale.setDefault(Resources.getSystem().configuration.locales[0])
        }
        return override
    }
}
