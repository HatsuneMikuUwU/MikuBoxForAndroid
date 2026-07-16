package top.uwu.mikubox

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import top.uwu.mikubox.core.AppSettings
import androidx.core.os.LocaleListCompat

/** Applies the persisted theme before any activity is created. */
class MikuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppSettings.nightMode(this))
        val language = AppSettings.language(this)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }
}
