package io.nekohasekai.sagernet.ui.bottomsheet

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.card.MaterialCardView
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher

private const val TRANSITION_DURATION = 300L

private fun View.toggleWithTransition(parentView: ViewGroup, isExpanding: Boolean) {
    TransitionManager.beginDelayedTransition(parentView, AutoTransition().setDuration(TRANSITION_DURATION))
    this.visibility = if (isExpanding) View.VISIBLE else View.GONE
}

private fun ImageView.animateRotation(endDegree: Float) {
    this.animate()
        .rotation(endDegree)
        .setDuration(TRANSITION_DURATION)
        .start()
}

/**
 * Bottom sheet replacement for the toolbar overflow ("⋮" / `action_misc`)
 * button's old nested popup submenu, ported from MikuRay's
 * `MoreMenuBottomSheet`: expandable "Quick Actions" and "Management" card
 * groups, plus a non-expandable "Group order" section using CheckedTextView
 * as a single-choice radio list.
 */
class MoreMenuBottomSheet : BaseBottomSheetFragment() {

    interface OnMoreOptionClickListener {
        fun onMoreOptionClicked(viewId: Int)
    }

    private var mListener: OnMoreOptionClickListener? = null
    private var groupId: Long = 0L
    private var currentOrder: Int = GroupOrder.ORIGIN

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mListener = parentFragment as? OnMoreOptionClickListener
            ?: context as? OnMoreOptionClickListener
            ?: throw RuntimeException("$context must implement OnMoreOptionClickListener")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getLong(ARG_GROUP_ID) ?: DataStore.currentGroupId()
        currentOrder = SagerDatabase.groupDao.getById(groupId)?.order ?: GroupOrder.ORIGIN
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.uwu_bottom_sheet_more_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.particles_view)?.isVisible = !DataStore.disableParticlesSheet
        loadBanner(view)

        val rootContainer = view.findViewById<ViewGroup>(R.id.menu_container_parent) ?: (view as ViewGroup)

        bindRow(view, R.id.quick_actions_expand_header, R.id.row_quick_actions_expand_header, R.drawable.ic_lightbulb_outline, R.string.quick_actions_and_testing)
        bindRow(view, R.id.action_connection_tcp_ping, R.id.row_action_connection_tcp_ping, R.drawable.ic_dns, R.string.connection_test_tcp_ping)
        bindRow(view, R.id.action_connection_url_test, R.id.row_action_connection_url_test, R.drawable.ic_crosshairs, R.string.connection_test_url_test)
        bindRow(view, R.id.action_update_subscription, R.id.row_action_update_subscription, R.drawable.ic_cloud, R.string.update_current_subscription)

        bindRow(view, R.id.management_expand_header, R.id.row_management_expand_header, R.drawable.ic_account_cog_outline, R.string.management)
        bindRow(view, R.id.action_clear_traffic_statistics, R.id.row_action_clear_traffic_statistics, R.drawable.ic_restore_24dp, R.string.clear_traffic_statistics)
        bindRow(view, R.id.action_remove_duplicate, R.id.row_action_remove_duplicate, R.drawable.ic_delete_24dp, R.string.remove_duplicate)
        bindRow(view, R.id.action_connection_test_clear_results, R.id.row_action_connection_test_clear_results, R.drawable.ic_refresh, R.string.connection_test_clear_results)
        bindRow(view, R.id.action_connection_test_delete_unavailable, R.id.row_action_connection_test_delete_unavailable, R.drawable.ic_delete_24dp, R.string.connection_test_delete_unavailable)

        setupExpandable(
            rootContainer,
            view.findViewById<View>(R.id.quick_actions_expand_header),
            view.findViewById<View>(R.id.quick_actions_expand_content),
            view.findViewById<MaterialCardView>(R.id.quick_actions_expand_header)
                ?.findViewById<View>(R.id.row_quick_actions_expand_header)
                ?.findViewById<ImageView>(R.id.row_arrow),
        )
        setupExpandable(
            rootContainer,
            view.findViewById<View>(R.id.management_expand_header),
            view.findViewById<View>(R.id.management_expand_content),
            view.findViewById<MaterialCardView>(R.id.management_expand_header)
                ?.findViewById<View>(R.id.row_management_expand_header)
                ?.findViewById<ImageView>(R.id.row_arrow),
        )

        val checkOrigin = view.findViewById<CheckedTextView>(R.id.action_order_origin)
        val checkName = view.findViewById<CheckedTextView>(R.id.action_order_by_name)
        val checkDelay = view.findViewById<CheckedTextView>(R.id.action_order_by_delay)

        fun updateChecks(order: Int) {
            checkOrigin?.isChecked = order == GroupOrder.ORIGIN
            checkName?.isChecked = order == GroupOrder.BY_NAME
            checkDelay?.isChecked = order == GroupOrder.BY_DELAY
        }
        updateChecks(currentOrder)

        view.findViewById<View>(R.id.card_order_origin)?.setOnClickListener {
            checkOrigin?.performClick()
        }
        view.findViewById<View>(R.id.card_order_by_name)?.setOnClickListener {
            checkName?.performClick()
        }
        view.findViewById<View>(R.id.card_order_by_delay)?.setOnClickListener {
            checkDelay?.performClick()
        }

        val orderClickListener = View.OnClickListener { v ->
            val newOrder = when (v.id) {
                R.id.action_order_origin -> GroupOrder.ORIGIN
                R.id.action_order_by_name -> GroupOrder.BY_NAME
                R.id.action_order_by_delay -> GroupOrder.BY_DELAY
                else -> currentOrder
            }
            if (newOrder != currentOrder) {
                currentOrder = newOrder
                updateChecks(newOrder)
                runOnDefaultDispatcher {
                    val group = SagerDatabase.groupDao.getById(groupId) ?: return@runOnDefaultDispatcher
                    group.order = newOrder
                    GroupManager.updateGroup(group)
                }
            }
            dismiss()
        }

        listOf(
            R.id.action_order_origin,
            R.id.action_order_by_name,
            R.id.action_order_by_delay,
        ).forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener(orderClickListener)
        }

        val clickListener = View.OnClickListener { v ->
            mListener?.onMoreOptionClicked(v.id)
            dismiss()
        }

        listOf(
            R.id.action_connection_tcp_ping,
            R.id.action_connection_url_test,
            R.id.action_update_subscription,
            R.id.action_clear_traffic_statistics,
            R.id.action_remove_duplicate,
            R.id.action_connection_test_clear_results,
            R.id.action_connection_test_delete_unavailable,
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

    private fun setupExpandable(
        parent: ViewGroup,
        toggleHeader: View?,
        expandableContent: View?,
        arrowIcon: ImageView?,
    ) {
        if (toggleHeader == null || expandableContent == null) return

        arrowIcon?.rotation = if (expandableContent.isVisible) 90f else 0f

        toggleHeader.setOnClickListener {
            val isExpanding = expandableContent.visibility == View.GONE
            expandableContent.toggleWithTransition(parent, isExpanding)
            arrowIcon?.animateRotation(if (isExpanding) 90f else 0f)
        }
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
        const val TAG = "MoreMenuBottomSheet"
        private const val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"
        private const val ARG_GROUP_ID = "groupId"

        fun newInstance(groupId: Long): MoreMenuBottomSheet {
            return MoreMenuBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_GROUP_ID, groupId)
                }
            }
        }
    }
}
