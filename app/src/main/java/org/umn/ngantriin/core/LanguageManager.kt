package org.umn.ngantriin.core

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Section 43: per-app language, independent of the device's system language.
 *
 * Talks to the platform's `LocaleManager` directly on API 33+ rather than
 * through `AppCompatDelegate.setApplicationLocales()`: on this project's
 * ComponentActivity (not an AppCompatActivity), AppCompatDelegate's bridge to
 * the platform API turned out not to round-trip reliably — `setApplicationLocales`
 * would return without error, but `cmd locale get-app-locales` still showed
 * nothing set, and `getApplicationLocales()` kept reading back empty. Setting
 * `LocaleManager.applicationLocales` straight from here is exactly what `adb
 * shell cmd locale set-app-locales` does under the hood, confirmed to persist
 * and to trigger the automatic activity recreation on its own.
 *
 * AppCompatDelegate is kept only for API < 33, where there is no platform
 * LocaleManager and its ContentProvider-backed storage plus a manual
 * [Activity.recreate] is the only way to make the change stick and show.
 */
object LanguageManager {
    const val ENGLISH = "en"
    const val INDONESIAN = "id"

    /** [Locale.getLanguage] normalises "id" to the legacy "in" code on some runtimes. */
    private const val INDONESIAN_LEGACY = "in"

    fun current(context: Context): String {
        val language = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?.get(0)
                ?.language
        } else {
            AppCompatDelegate.getApplicationLocales().get(0)?.language
        }
        return if (language == INDONESIAN_LEGACY) INDONESIAN else language ?: ENGLISH
    }

    fun setLanguage(tag: String, context: Context, activity: Activity?) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(tag)
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            activity?.recreate()
        }
    }
}
