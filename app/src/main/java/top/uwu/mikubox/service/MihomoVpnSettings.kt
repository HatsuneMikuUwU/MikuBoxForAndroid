package top.uwu.mikubox.service

import android.content.Context

/** Non-UI VPN settings equivalent to UwU's service, MTU and per-app settings. */
object MihomoVpnSettings {

    enum class AppMode { ALL, ALLOW_LIST, DISALLOW_LIST }

    private const val PREFS = "mihomo_vpn_settings"
    private const val MTU = "mtu"
    private const val APP_MODE = "app_mode"
    private const val PACKAGES = "packages"

    fun mtu(context: Context): Int = prefs(context).getInt(MTU, 9000).coerceIn(1280, 65_535)

    fun setMtu(context: Context, value: Int) {
        prefs(context).edit().putInt(MTU, value.coerceIn(1280, 65_535)).commit()
    }

    fun appMode(context: Context): AppMode = runCatching {
        AppMode.valueOf(prefs(context).getString(APP_MODE, AppMode.ALL.name)!!)
    }.getOrDefault(AppMode.ALL)

    fun setAppMode(context: Context, mode: AppMode) {
        prefs(context).edit().putString(APP_MODE, mode.name).commit()
    }

    fun packages(context: Context): Set<String> =
        prefs(context).getStringSet(PACKAGES, emptySet())?.toSet().orEmpty()

    fun setPackages(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(PACKAGES, packages).commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
