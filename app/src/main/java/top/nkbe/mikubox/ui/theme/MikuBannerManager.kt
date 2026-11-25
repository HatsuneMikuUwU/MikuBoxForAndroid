package top.nkbe.mikubox.ui.theme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import kotlin.random.Random

data class MikuBannerItem(
    val key: String,
    @StringRes val titleRes: Int,
    @DrawableRes val drawableRes: Int,
)

object MikuBannerManager {

    val BANNERS: List<MikuBannerItem> = listOf(
        MikuBannerItem("uwu_banner_home", R.string.banner_theme_home, R.drawable.uwu_banner_home),
        MikuBannerItem("uwu_banner_stage", R.string.banner_theme_stage, R.drawable.uwu_banner_stage),
        MikuBannerItem("uwu_banner_theme", R.string.banner_theme_wardrobe, R.drawable.uwu_banner_theme),
        MikuBannerItem("uwu_banner_author", R.string.banner_theme_author, R.drawable.uwu_banner_author),
        MikuBannerItem("uwu_banner_sheet", R.string.banner_theme_sheet, R.drawable.uwu_banner_sheet),
        MikuBannerItem("uwu_banner_selected", R.string.banner_theme_selected, R.drawable.uwu_banner_selected),
        MikuBannerItem("uwu_banner_image_about", R.string.banner_theme_about, R.drawable.uwu_banner_image_about),
        MikuBannerItem("uwu_banner_image", R.string.banner_theme_miracle, R.drawable.uwu_banner_image),
        MikuBannerItem("uwu_banner_splash", R.string.banner_theme_splash, R.drawable.uwu_banner_splash),
        MikuBannerItem("uwu_banner_lakeside", R.string.banner_theme_lakeside, R.drawable.uwu_banner_lakeside),
        MikuBannerItem("uwu_banner_profile", R.string.banner_theme_iconic, R.drawable.uwu_banner_profile),
    )

    // The square avatar art crops poorly as a wide banner, so keep it out of the random pool.
    private val RANDOM_POOL: List<String> =
        BANNERS.map { it.key }.filter { it != "uwu_banner_profile" }

    // Cached random key generated once per session to maintain consistency until relaunch
    private val sessionRandomKey: String by lazy {
        RANDOM_POOL.random(Random(System.currentTimeMillis()))
    }

    @DrawableRes
    fun getCurrentHomeBannerRes(): Int {
        if (DataStore.randomizeBannerOnLaunch) {
            return resolveBannerDrawable(sessionRandomKey)
        }
        val key = DataStore.homeBannerTheme
        if (key == "random") {
            return resolveBannerDrawable(sessionRandomKey)
        }
        return resolveBannerDrawable(key)
    }

    @DrawableRes
    fun resolveBannerDrawable(key: String?): Int =
        BANNERS.firstOrNull { it.key == key }?.drawableRes ?: R.drawable.uwu_banner_home
}
