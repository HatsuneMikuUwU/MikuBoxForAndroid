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
        MikuBannerItem("uwu_banner_profile", R.string.banner_theme_iconic, R.drawable.uwu_banner_profile),
    )

    private val RANDOM_POOL: List<String> = listOf(
        "uwu_banner_home",
        "uwu_banner_stage",
        "uwu_banner_theme",
        "uwu_banner_author",
        "uwu_banner_sheet",
        "uwu_banner_selected",
        "uwu_banner_image_about",
        "uwu_banner_image",
        "uwu_banner_splash",
    )

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
    fun resolveBannerDrawable(key: String?): Int = when (key) {
        "uwu_banner_stage" -> R.drawable.uwu_banner_stage
        "uwu_banner_theme" -> R.drawable.uwu_banner_theme
        "uwu_banner_author" -> R.drawable.uwu_banner_author
        "uwu_banner_sheet" -> R.drawable.uwu_banner_sheet
        "uwu_banner_selected" -> R.drawable.uwu_banner_selected
        "uwu_banner_image_about" -> R.drawable.uwu_banner_image_about
        "uwu_banner_image" -> R.drawable.uwu_banner_image
        "uwu_banner_splash" -> R.drawable.uwu_banner_splash
        "uwu_banner_profile" -> R.drawable.uwu_banner_profile
        else -> R.drawable.uwu_banner_home
    }
}
