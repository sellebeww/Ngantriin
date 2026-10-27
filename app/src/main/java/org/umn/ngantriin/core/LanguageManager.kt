package org.umn.ngantriin.core

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Bahasa per aplikasi, terpisah dari bahasa sistem perangkat.
 *
 * Di API 33+ langsung memakai `LocaleManager` milik platform, bukan lewat
 * `AppCompatDelegate.setApplicationLocales()`. Di ComponentActivity proyek ini
 * (bukan AppCompatActivity), jembatan AppCompatDelegate ke API platform ternyata
 * tidak konsisten: `setApplicationLocales` kembali tanpa error, tapi
 * `cmd locale get-app-locales` tetap kosong dan `getApplicationLocales()` terus
 * membaca kosong. Mengisi `LocaleManager.applicationLocales` langsung dari sini
 * sama dengan `adb shell cmd locale set-app-locales`, dan sudah dicek bahwa
 * tersimpan dan activity otomatis dibuat ulang.
 *
 * AppCompatDelegate hanya dipakai untuk API < 33, yang tidak punya LocaleManager.
 * Di sana penyimpanan berbasis ContentProvider ditambah [Activity.recreate]
 * manual adalah satu-satunya cara supaya perubahan bertahan dan terlihat.
 */
object LanguageManager {
    const val ENGLISH = "en"
    const val INDONESIAN = "id"

    /** [Locale.getLanguage] di beberapa runtime mengubah "id" jadi kode lama "in". */
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

    /**
     * Bahasa yang benar-benar dipakai UI: pilihan per aplikasi kalau ada,
     * kalau tidak bahasa perangkat. Hanya "id" atau "en", dua bahasa yang
     * disediakan, supaya backend bisa menulis push dengan bahasa yang sama.
     */
    fun effective(context: Context): String {
        val language = context.resources.configuration.locales.get(0)?.language
        return if (language == INDONESIAN || language == INDONESIAN_LEGACY) INDONESIAN else ENGLISH
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
