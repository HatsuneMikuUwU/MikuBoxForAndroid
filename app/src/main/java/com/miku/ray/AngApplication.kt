package com.miku.ray

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import androidx.work.Configuration
import androidx.work.WorkManager
import com.miku.ray.AppConfig
import com.miku.ray.AppConfig.ANG_PACKAGE
import com.miku.ray.extension.ForegroundActivityTracker
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.ThemeManager
import com.miku.ray.util.CustomFontManager
import com.miku.ray.util.AppFontResolver
import com.miku.ray.util.CrashHandler

/**
 * `open` for one reason: the application class in this project inherits from it.
 * MikuRay's view models are `AndroidViewModel`s typed to this class and its
 * activity callbacks are what apply the theme, font and locale, so the host app
 * must *be* an AngApplication rather than merely call into one.
 */
open class AngApplication : Application(), Application.ActivityLifecycleCallbacks {
    companion object {
        lateinit var application: AngApplication

        fun getCustomTypeface(context: Context, fontName: String? = null): Typeface? {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)) {
                return CustomFontManager.getTypeface(context)
            }
            val name = fontName ?: MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT)
            return AppFontResolver.getTypeface(context, name)
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
    .setDefaultProcessName("${ANG_PACKAGE}:bg")
    .build()

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install()

        MmkvManager.initialize(this)
        SettingsManager.initApp(this)

        ForegroundActivityTracker.register(this)
        registerActivityLifecycleCallbacks(this)
        WorkManager.initialize(this, workManagerConfiguration)
        SettingsManager.setNightMode()
    }

    fun applyActivityTheme(activity: Activity) {
        ThemeManager.applyTheme(activity)

        CustomFontManager.restoreGlobalOverride()

        val useCustomFont = MmkvManager.decodeSettingsBool(AppConfig.PREF_APP_FONT_USE_CUSTOM, false)
        val typeface = if (useCustomFont) {
            CustomFontManager.getTypeface(activity)
        } else {
            val fontName = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_FONT)
            AppFontResolver.getTypeface(activity, fontName)
        }
        typeface?.let { CustomFontManager.applyGlobalOverride(it) }

        val isTrueBlack = ThemeManager.isDarkMode(activity) && MmkvManager.decodeSettingsBool(AppConfig.PREF_TRUE_BLACK, false)
        if (isTrueBlack) {
            activity.theme.applyStyle(R.style.ThemeOverlay_App_TrueBlack_DialogFix, true)
        }
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        applyActivityTheme(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            applyActivityTheme(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        val hide = MmkvManager.decodeSettingsBool(AppConfig.PREF_HIDE_FROM_RECENT_APPS, false)
        try {
            val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val tasks = activityManager.appTasks
            if (tasks.isNotEmpty()) {
                tasks[0].setExcludeFromRecents(hide)
            }
        } catch (e: Exception) {
        }
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
