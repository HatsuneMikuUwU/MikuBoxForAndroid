package io.nekohasekai.sagernet.ui.bottomsheet

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.card.MaterialCardView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.widget.marquee.AutoMarqueeTextView

/**
 * Bottom sheet replacement for the old side [androidx.drawerlayout.widget.DrawerLayout]
 * navigation drawer, ported 1:1 from MikuRay's `uwu_layout_bottom_sheet_main_menu`
 * (banner + particles + profile header, grouped card list, tools & info row).
 */
class MainMenuBottomSheet : BaseBottomSheetFragment() {

    interface OnOptionClickListener {
        fun onMenuOptionClicked(viewId: Int)
    }

    private var mListener: OnOptionClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mListener = context as? OnOptionClickListener
            ?: throw RuntimeException("$context must implement OnOptionClickListener")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.uwu_layout_bottom_sheet_main_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.particles_view)?.isVisible = !DataStore.disableParticlesSheet
        loadBanner(view)

        // grouped list rows: icon in a colored badge, title, trailing chevron
        bindRow(view, R.id.menu_group, R.id.row_menu_group, R.drawable.ic_subscriptions_24dp, R.string.menu_group)
        bindRow(view, R.id.menu_route, R.id.row_menu_route, R.drawable.ic_routing_24dp, R.string.menu_route)
        bindRow(view, R.id.menu_settings, R.id.row_menu_settings, R.drawable.ic_settings_24dp, R.string.settings)

        // tools & info horizontal-scroll badges: icon, title, description
        bindBadge(view, R.id.menu_logcat, R.id.badge_menu_logcat, R.drawable.ic_logcat_24dp, R.string.menu_log, R.string.desc_menu_log)
        bindBadge(view, R.id.menu_traffic, R.id.badge_menu_traffic, R.drawable.ic_baseline_transform_24, R.string.menu_dashboard, R.string.desc_menu_dashboard)
        bindBadge(view, R.id.menu_tools, R.id.badge_menu_tools, R.drawable.baseline_construction_24, R.string.menu_tools, R.string.desc_menu_tools)
        bindBadge(view, R.id.menu_about, R.id.badge_menu_about, R.drawable.ic_about_24dp, R.string.menu_about, R.string.desc_menu_about)

        // dashboard is only meaningful when the sing-box clash API is enabled
        view.findViewById<View>(R.id.menu_traffic)?.isVisible = DataStore.enableClashAPI

        val clickListener = View.OnClickListener {
            mListener?.onMenuOptionClicked(it.id)
            dismiss()
        }

        listOf(
            R.id.menu_group,
            R.id.menu_route,
            R.id.menu_settings,
            R.id.menu_logcat,
            R.id.menu_traffic,
            R.id.menu_tools,
            R.id.menu_about,
        ).forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(clickListener)
        }
    }

    private fun bindRow(
        root: View,
        cardId: Int,
        rowId: Int,
        iconRes: Int,
        titleRes: Int,
    ) {
        val card = root.findViewById<MaterialCardView>(cardId) ?: return
        val row = card.findViewById<View>(rowId) ?: return
        row.findViewById<ImageView>(R.id.row_icon)?.setImageResource(iconRes)
        row.findViewById<TextView>(R.id.row_title)?.setText(titleRes)
    }

    private fun bindBadge(
        root: View,
        cardId: Int,
        badgeId: Int,
        iconRes: Int,
        titleRes: Int,
        descRes: Int,
    ) {
        val card = root.findViewById<MaterialCardView>(cardId) ?: return
        val badge = card.findViewById<View>(badgeId) ?: return
        badge.findViewById<ImageView>(R.id.badge_icon)?.setImageResource(iconRes)
        badge.findViewById<TextView>(R.id.badge_title)?.setText(titleRes)
        badge.findViewById<AutoMarqueeTextView>(R.id.badge_desc)?.setText(descRes)
    }

    private fun loadBanner(view: View) {
        val bannerImageView = view.findViewById<ImageView>(R.id.img_banner_sheet) ?: return
        bannerImageView.setLayerType(View.LAYER_TYPE_NONE, null)
        val uriString = DataStore.customSheetBannerUri
        val targetTag = uriString.ifBlank { TAG_SHEET_DEFAULT }
        if (bannerImageView.tag != targetTag) {
            if (uriString.isNotBlank()) {
                val isGif = uriString.lowercase().endsWith(".gif")
                val request = if (isGif) {
                    Glide.with(this).asGif().load(Uri.parse(uriString))
                } else {
                    Glide.with(this).load(Uri.parse(uriString))
                }
                request
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .error(R.drawable.uwu_banner_sheet)
                    .into(bannerImageView)
            } else {
                Glide.with(this).clear(bannerImageView)
                bannerImageView.setImageResource(R.drawable.uwu_banner_sheet)
            }
            bannerImageView.tag = targetTag
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    companion object {
        const val TAG = "MainMenuBottomSheet"
        private const val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"
    }
}
