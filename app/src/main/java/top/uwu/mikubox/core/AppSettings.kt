package top.uwu.mikubox.core

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** App-wide UI preferences (theme, effects) equivalent to UwU's DataStore flags. */
object AppSettings {

    private const val PREFS = "miku_app_settings"
    private const val KEY_NIGHT_MODE = "night_mode"
    private const val KEY_PARTICLES = "particles"

    /** One of [AppCompatDelegate].MODE_NIGHT_FOLLOW_SYSTEM / _NO / _YES. */
    fun nightMode(context: Context): Int =
        prefs(context).getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    fun setNightMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_NIGHT_MODE, mode).commit()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun particlesEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PARTICLES, true)

    fun setParticlesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PARTICLES, enabled).commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
