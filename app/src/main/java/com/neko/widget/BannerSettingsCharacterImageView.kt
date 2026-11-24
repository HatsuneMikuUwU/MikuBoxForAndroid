package com.neko.widget

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener

/**
 * Displays the user-selected Miku character illustration inside the Settings banner
 * and refreshes immediately when the preference changes.
 */
class BannerSettingsCharacterImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), OnPreferenceDataStoreChangeListener {

    init {
        applyCharacter()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            DataStore.configurationStore.registerChangeListener(this)
            applyCharacter()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            DataStore.configurationStore.unregisterChangeListener(this)
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (key == KEY_CHARACTER) {
            post { applyCharacter() }
        }
    }

    private fun applyCharacter() {
        val resId = if (isInEditMode) {
            R.drawable.uwu_banner_miku_v6
        } else {
            resolveDrawable(DataStore.bannerSettingsCharacter)
        }
        setImageResource(resId)
    }

    companion object {
        const val KEY_CHARACTER = "banner_settings_character"

        @DrawableRes
        fun resolveDrawable(key: String?): Int = when (key) {
            "uwu_icon_deep_sea_girl" -> R.drawable.uwu_icon_deep_sea_girl
            "uwu_icon_mesmerizer" -> R.drawable.uwu_icon_mesmerizer
            "uwu_icon_rabbit_hole" -> R.drawable.uwu_icon_rabbit_hole
            "uwu_icon_cinnamiku" -> R.drawable.uwu_icon_cinnamiku
            "uwu_icon_magical_mirai_2024" -> R.drawable.uwu_icon_magical_mirai_2024
            "uwu_icon_racing_miku_2025" -> R.drawable.uwu_icon_racing_miku_2025
            "uwu_icon_snow_miku_2025" -> R.drawable.uwu_icon_snow_miku_2025
            "uwu_icon_sakura" -> R.drawable.uwu_icon_sakura
            "uwu_icon_symphony_2022" -> R.drawable.uwu_icon_symphony_2022
            "uwu_icon_cherry_pop" -> R.drawable.uwu_icon_cherry_pop
            "uwu_icon_retry_now" -> R.drawable.uwu_icon_retry_now
            "uwu_icon_miku" -> R.drawable.uwu_icon_miku
            "uwu_banner_image_2" -> R.drawable.uwu_banner_image_2
            "uwu_banner_miku_v2" -> R.drawable.uwu_banner_miku_v2
            "uwu_banner_miku_v2_chinese" -> R.drawable.uwu_banner_miku_v2_chinese
            "uwu_banner_miku_v2_append" -> R.drawable.uwu_banner_miku_v2_append
            "uwu_banner_miku_v3" -> R.drawable.uwu_banner_miku_v3
            "uwu_banner_miku_v3_english" -> R.drawable.uwu_banner_miku_v3_english
            "uwu_banner_miku_v4" -> R.drawable.uwu_banner_miku_v4
            "uwu_banner_miku_super_pack" -> R.drawable.uwu_banner_miku_super_pack
            "uwu_banner_miku_v6" -> R.drawable.uwu_banner_miku_v6
            else -> R.drawable.uwu_banner_miku_v6
        }
    }
}
