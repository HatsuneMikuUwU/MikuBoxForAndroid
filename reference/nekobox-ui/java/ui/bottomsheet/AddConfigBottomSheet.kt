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
 * Bottom sheet replacement for the toolbar "+" button's old nested popup
 * submenu, ported from MikuRay's `AddConfigBottomSheet` (banner + particles
 * header, "Import Configuration" group, "Manual Setup" group).
 */
class AddConfigBottomSheet : BaseBottomSheetFragment() {

    interface OnAddConfigClickListener {
        fun onAddConfigOptionClicked(viewId: Int)
    }

    private var mListener: OnAddConfigClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mListener = parentFragment as? OnAddConfigClickListener
            ?: context as? OnAddConfigClickListener
            ?: throw RuntimeException("$context must implement OnAddConfigClickListener")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.uwu_layout_bottom_sheet_add_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.particles_view)?.isVisible = !DataStore.disableParticlesSheet
        loadBanner(view)

        bindRow(view, R.id.import_qrcode, R.id.row_import_qrcode, R.drawable.ic_action_qr, R.string.add_profile_methods_scan_qr_code)
        bindRow(view, R.id.import_clipboard, R.id.row_import_clipboard, R.drawable.ic_action_clipboard, R.string.action_import)
        bindRow(view, R.id.import_local, R.id.row_import_local, R.drawable.ic_action_file, R.string.action_import_file)

        bindManualBadge(view, R.id.import_manually_socks, R.id.badge_import_manually_socks, R.string.action_socks)
        bindManualBadge(view, R.id.import_manually_http, R.id.badge_import_manually_http, R.string.action_http)
        bindManualBadge(view, R.id.import_manually_ss, R.id.badge_import_manually_ss, R.string.action_shadowsocks)
        bindManualBadge(view, R.id.import_manually_vmess, R.id.badge_import_manually_vmess, R.string.action_vmess)
        bindManualBadge(view, R.id.import_manually_vless, R.id.badge_import_manually_vless, R.string.action_vless)
        bindManualBadge(view, R.id.import_manually_trojan, R.id.badge_import_manually_trojan, R.string.action_trojan)
        bindManualBadge(view, R.id.import_manually_trojan_go, R.id.badge_import_manually_trojan_go, R.string.action_trojan_go)
        bindManualBadge(view, R.id.import_manually_mieru, R.id.badge_import_manually_mieru, R.string.action_mieru)
        bindManualBadge(view, R.id.import_manually_naive, R.id.badge_import_manually_naive, R.string.action_naive)
        bindManualBadge(view, R.id.import_manually_hysteria, R.id.badge_import_manually_hysteria, R.string.action_hysteria)
        bindManualBadge(view, R.id.import_manually_tuic, R.id.badge_import_manually_tuic, R.string.action_tuic)
        bindManualBadge(view, R.id.import_manually_shadowtls, R.id.badge_import_manually_shadowtls, R.string.action_shadowtls)
        bindManualBadge(view, R.id.import_manually_anytls, R.id.badge_import_manually_anytls, R.string.action_anytls)
        bindManualBadge(view, R.id.import_manually_ssh, R.id.badge_import_manually_ssh, R.string.action_ssh)
        bindManualBadge(view, R.id.import_manually_wg, R.id.badge_import_manually_wg, R.string.action_wireguard)
        bindManualBadge(view, R.id.import_manually_config, R.id.badge_import_manually_config, R.string.custom_config)
        bindManualBadge(view, R.id.import_manually_chain, R.id.badge_import_manually_chain, R.string.proxy_chain)

        val clickListener = View.OnClickListener {
            mListener?.onAddConfigOptionClicked(it.id)
            dismiss()
        }

        listOf(
            R.id.import_qrcode, R.id.import_clipboard, R.id.import_local,
            R.id.import_manually_socks, R.id.import_manually_http, R.id.import_manually_ss,
            R.id.import_manually_vmess, R.id.import_manually_vless, R.id.import_manually_trojan,
            R.id.import_manually_trojan_go, R.id.import_manually_mieru, R.id.import_manually_naive,
            R.id.import_manually_hysteria, R.id.import_manually_tuic, R.id.import_manually_shadowtls,
            R.id.import_manually_anytls, R.id.import_manually_ssh, R.id.import_manually_wg,
            R.id.import_manually_config, R.id.import_manually_chain,
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

    private fun bindManualBadge(root: View, cardId: Int, badgeId: Int, protocolNameRes: Int) {
        val card = root.findViewById<MaterialCardView>(cardId) ?: return
        val badge = card.findViewById<View>(badgeId) ?: return
        val protocolName = getString(protocolNameRes)
        badge.findViewById<ImageView>(R.id.badge_icon)?.setImageResource(R.drawable.ic_action_note_add)
        badge.findViewById<TextView>(R.id.badge_title)?.setText(protocolNameRes)
        badge.findViewById<AutoMarqueeTextView>(R.id.badge_desc)?.text =
            getString(R.string.desc_import_manually, protocolName)
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
        const val TAG = "AddConfigBottomSheet"
        private const val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"
    }
}
