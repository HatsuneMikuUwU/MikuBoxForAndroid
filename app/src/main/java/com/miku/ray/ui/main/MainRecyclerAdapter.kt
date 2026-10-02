package com.miku.ray.ui.main

import com.miku.ray.remixicon.R as RemixR
import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.coroutines.launch
import com.miku.ray.R
import com.miku.ray.contracts.MainAdapterListener
import com.miku.ray.databinding.ItemRecyclerFooterBinding
import com.miku.ray.databinding.ItemRecyclerMainBinding
import com.miku.ray.databinding.ItemRecyclerMainGridBinding
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.dto.entities.ServersCache
import com.miku.ray.enums.EConfigType
import com.miku.ray.extension.isComplexType
import com.miku.ray.handler.MmkvManager
import com.miku.ray.helper.ItemTouchHelperAdapter
import com.miku.ray.helper.ItemTouchHelperViewHolder
import java.util.Collections
import com.miku.ray.util.IndicatorStyle
import com.miku.ray.util.SelectedProfileBannerController
import com.miku.ray.util.SensorTextController
import com.miku.ray.util.getColorAttr
import com.miku.ray.util.Utils
import com.miku.ray.AppConfig

class MainRecyclerAdapter(
    private val mainViewModel: MainViewModel,
    private val adapterListener: MainAdapterListener?
) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>(), ItemTouchHelperAdapter,
FastScrollRecyclerView.SectionedAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM_LIST = 1
        private const val VIEW_TYPE_FOOTER = 2
        private const val VIEW_TYPE_ITEM_GRID = 3
    }

    override fun getSectionName(position: Int): String {
        val remarks = data.getOrNull(position)?.profile?.remarks.orEmpty()
        return remarks.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: ""
    }

    private var data: MutableList<ServersCache> = mutableListOf()
    private var isGridMode: Boolean = false

    val isServerListEmpty: Boolean
    get() = data.isEmpty()

    @SuppressLint("NotifyDataSetChanged")
    fun setGridMode(gridMode: Boolean) {
        if (isGridMode != gridMode) {
            isGridMode = gridMode
            notifyDataSetChanged()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshDisplayPrefs() {
        notifyDataSetChanged()
    }

    private var isRunningCollectJob: kotlinx.coroutines.Job? = null
    private var selectedBannerController: SelectedProfileBannerController? = null

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newData: MutableList<ServersCache>?, position: Int = -1) {
        data = newData?.toMutableList() ?: mutableListOf()

        if (position >= 0 && position in data.indices) {
            notifyServerItemChanged(position)
        } else {
            notifyDataSetChanged()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyServerItemChanged(position: Int) {
        if (isGridMode || position !in data.indices) {
            notifyDataSetChanged()
        } else {
            notifyItemChanged(position)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val lifecycleOwner = recyclerView.context as? androidx.lifecycle.LifecycleOwner
        if (lifecycleOwner != null) {
            isRunningCollectJob?.cancel()
            isRunningCollectJob = lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    mainViewModel.isRunning.collect {
                        val selectedGuid = MmkvManager.getSelectServer()
                        val position = data.indexOfFirst { it.guid == selectedGuid }
                        if (position >= 0) {
                            notifyServerItemChanged(position)
                        } else if (data.isNotEmpty()) {
                            notifyDataSetChanged()
                        }
                    }
                }
            }
        }

        val controller = SelectedProfileBannerController(recyclerView.context)
        selectedBannerController = controller
        lifecycleOwner?.let { owner ->
            controller.registerChangeListener(owner.lifecycleScope) {
                val selectedGuid = MmkvManager.getSelectServer()
                val position = data.indexOfFirst { it.guid == selectedGuid }
                if (position >= 0) {
                    notifyServerItemChanged(position)
                }
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        isRunningCollectJob?.cancel()
        isRunningCollectJob = null
        selectedBannerController?.unregisterChangeListener()
        selectedBannerController = null
    }

    override fun getItemCount() = data.size + 1

    override fun onViewRecycled(holder: BaseViewHolder) {
        if (holder is MainViewHolder) {
            val statusDrawable = holder.views.vStatusDot.background
            if (statusDrawable is android.graphics.drawable.AnimationDrawable) {
                statusDrawable.stop()
            }
            holder.views.layoutIndicator?.let { selectedBannerController?.clear(it) }
            holder.views.infoContainer.setOnTouchListener(null)
            holder.views.layoutMore?.setOnClickListener(null)
            holder.views.layoutShare?.setOnClickListener(null)
            holder.views.layoutEdit?.setOnClickListener(null)
            holder.views.layoutRemove?.setOnClickListener(null)
        }
        super.onViewRecycled(holder)
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val context = holder.views.root.context
            val guid = data[position].guid
            val profile = data[position].profile

            holder.itemView.setBackgroundColor(Color.TRANSPARENT)

            holder.views.tvName.text = profile.remarks
            holder.views.tvStatistics.text = if (profile.configType == EConfigType.POLICYGROUP) {
                getPolicyGroupSubText(context, profile)
            } else {
                SensorTextController.getAddress(profile)
            }
            holder.views.tvType.text = getProtocolName(profile)

            val isNetSecEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_NETWORK_SECURITY_ENABLED) == true
            bindNetworkSecurity(holder, profile, isNetSecEnabled)

            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            val testDelayMillis = aff?.testDelayMillis ?: 0L
            val testResult = when {
                testDelayMillis > 0L -> aff?.getTestDelayString().orEmpty()
                testDelayMillis < 0L -> context.getString(R.string.connection_test_fail)
                else -> ""
            }
            holder.views.tvTestResult.text = testResult
            val rawCountryCode = aff?.countryCode?.trim()?.uppercase()
            val countryTestFailed = rawCountryCode == AppConfig.COUNTRY_CODE_TEST_FAILED
            val countryCode = rawCountryCode?.takeIf { it.length == 2 }
            val countryFlag = Utils.countryCodeToFlag(countryCode)
            holder.views.tvCountryCode.text = if (countryTestFailed) {
                context.getString(R.string.connection_test_fail)
            } else {
                listOf(countryFlag, countryCode)
                .filterNotNull()
                .filter { it.isNotBlank() }
                .joinToString(" ")
            }
            holder.views.tvCountryCode.setTextColor(
                if (countryTestFailed) {
                    ContextCompat.getColor(context, R.color.colorPingRed)
                } else {
                    context.getColorAttr("colorOnSurfaceVariant")
                }
            )
            holder.views.tvCountryCode.visibility =
            if (countryCode != null || countryTestFailed) View.VISIBLE else View.GONE
            holder.views.layoutTestMetadata?.visibility =
            if (testResult.isNotEmpty() || countryCode != null || countryTestFailed) View.VISIBLE else View.GONE
            if (testDelayMillis < 0L) {
                holder.views.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPingRed))
            } else {
                holder.views.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.colorPing))
            }
            // A disconnected tunnel keeps its last measured numbers in the
            // store; showing them at full strength read as "this delay is
            // current" right after a disconnect. Dim the stale readings (the
            // values stay — they come back at full strength on reconnect).
            val disconnectedDim = if (mainViewModel.isRunning.value) 1f else 0.4f
            holder.views.tvTestResult.alpha = disconnectedDim
            holder.views.tvCountryCode.alpha = disconnectedDim
            holder.views.layoutTestMetadata?.alpha = disconnectedDim

            val isTrafficEnabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_TRAFFIC_ENABLED) == true
            val trafficStr = MmkvManager.getProfileTrafficString(guid)

            if (isTrafficEnabled && !trafficStr.isNullOrEmpty()) {
                holder.views.tvTraffic.text = trafficStr
                holder.views.tvTraffic.visibility = View.VISIBLE
            } else {
                holder.views.tvTraffic.visibility = View.GONE
            }

            val isPinned = MmkvManager.isServerPinned(guid)
            holder.views.ivPinIndicator.visibility = if (isPinned) View.VISIBLE else View.GONE

            val isSelectedServer = (guid == MmkvManager.getSelectServer())
            val isVpnConnected = mainViewModel.isRunning.value

            if (isSelectedServer && isVpnConnected) {

                holder.views.vStatusDot.setBackgroundResource(R.drawable.blink_color)
                val blinkAnimDrawable = holder.views.vStatusDot.background

                if (blinkAnimDrawable is android.graphics.drawable.AnimationDrawable) {
                    holder.views.vStatusDot.visibility = View.VISIBLE

                    if (blinkAnimDrawable.isRunning) {
                        blinkAnimDrawable.stop()
                    }
                    holder.views.vStatusDot.post {
                        val d = holder.views.vStatusDot.background
                        if (d is android.graphics.drawable.AnimationDrawable && !d.isRunning) {
                            d.start()
                        }
                    }
                }
            } else {
                val blinkAnimDrawable = holder.views.vStatusDot.background
                if (blinkAnimDrawable is android.graphics.drawable.AnimationDrawable) {
                    blinkAnimDrawable.stop()
                }
                holder.views.vStatusDot.visibility = View.GONE
                holder.views.vStatusDot.background = null
            }

            if (isGridMode) {
                holder.views.layoutIndicator?.let { indicator ->
                    selectedBannerController?.clear(indicator)
                    indicator.setBackgroundResource(0)
                }
                holder.views.layoutCard.setCardBackgroundColor(context.getColorAttr("colorCard"))

                if (isSelectedServer) {
                    val strokeWidthPx = (3 * context.resources.displayMetrics.density).toInt()
                    holder.views.layoutCard.strokeWidth = strokeWidthPx
                    holder.views.layoutCard.setStrokeColor(context.getColorAttr("colorPrimary"))
                } else {
                    holder.views.layoutCard.strokeWidth = 0
                }
            } else if (isSelectedServer) {
                val styleName = MmkvManager.decodeSettingsString(
                    AppConfig.PREF_INDICATOR_STYLE,
                    IndicatorStyle.STYLE_0.name
                ) ?: IndicatorStyle.STYLE_0.name
                val indicatorStyle = runCatching {
                    IndicatorStyle.valueOf(styleName)
                }.getOrDefault(IndicatorStyle.STYLE_0)

                val bannerController = selectedBannerController
                holder.views.layoutIndicator?.let { indicator ->
                    if (bannerController != null && bannerController.isEnabled() && bannerController.hasBanner()) {
                        bannerController.applyTo(indicator)
                    } else {
                        bannerController?.clear(indicator)
                        indicator.setBackgroundResource(indicatorStyle.drawableRes)
                    }
                }
                holder.views.layoutCard.strokeWidth = 0
                holder.views.layoutCard.setCardBackgroundColor(Color.TRANSPARENT)
            } else {
                holder.views.layoutIndicator?.let { indicator ->
                    selectedBannerController?.clear(indicator)
                    indicator.setBackgroundResource(0)
                }
                holder.views.layoutCard.strokeWidth = 0
                holder.views.layoutCard.setCardBackgroundColor(context.getColorAttr("colorCard"))
            }

            val subRemarks = getSubscriptionRemarks(profile)
            holder.views.tvSubscription.text = subRemarks

            val isSubVisible = if (subRemarks.isEmpty()) View.GONE else View.VISIBLE
            holder.views.tvSubscription.visibility = isSubVisible
            holder.views.layoutSubscription.visibility = isSubVisible

            if (holder.views.isGridItem) {
                holder.views.layoutMore?.apply {
                    visibility = View.VISIBLE
                    setOnClickListener { anchor ->
                        showServerActionsMenu(anchor, guid, profile)
                    }
                }
            } else {
                val isCompactListActions =
                    MmkvManager.decodeSettingsBool(AppConfig.PREF_COMPACT_LIST_ACTIONS) == true

                if (isCompactListActions) {
                    holder.views.layoutShare?.apply {
                        visibility = View.GONE
                        setOnClickListener(null)
                    }
                    holder.views.layoutEdit?.apply {
                        visibility = View.GONE
                        setOnClickListener(null)
                    }
                    holder.views.layoutRemove?.apply {
                        visibility = View.GONE
                        setOnClickListener(null)
                    }
                    holder.views.layoutMore?.apply {
                        visibility = View.VISIBLE
                        setOnClickListener { anchor ->
                            showServerActionsMenu(anchor, guid, profile)
                        }
                    }
                } else {
                    holder.views.layoutMore?.apply {
                        visibility = View.GONE
                        setOnClickListener(null)
                    }
                    holder.views.layoutShare?.apply {
                        visibility = View.VISIBLE
                        setOnClickListener {
                            if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                adapterListener?.onShare(guid, profile, holder.bindingAdapterPosition, false)
                            }
                        }
                    }
                    holder.views.layoutEdit?.apply {
                        visibility = View.VISIBLE
                        setOnClickListener {
                            if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                adapterListener?.onEdit(guid, holder.bindingAdapterPosition, profile)
                            }
                        }
                    }
                    holder.views.layoutRemove?.apply {
                        visibility = View.VISIBLE
                        setOnClickListener {
                            if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                adapterListener?.onRemove(guid, holder.bindingAdapterPosition)
                            }
                        }
                    }
                }
            }

            val gestureDetector = android.view.GestureDetector(
                context,
                object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                        adapterListener?.onSelectServer(guid)
                        return true
                    }

                    override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                        if (isSelectedServer) {
                            if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                adapterListener?.onPinToggle(guid, holder.bindingAdapterPosition, isPinned)
                            }
                        } else {
                            adapterListener?.onSelectServer(guid)
                        }
                        return true
                    }
                }
            )
            holder.views.infoContainer.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }
        }
    }

    private fun getSubscriptionRemarks(profile: ProfileItem): String {
        val subRemarks =
        if (mainViewModel.subscriptionId.isEmpty())
        MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks
        else
        null

        return subRemarks?.take(5) ?: ""
    }

    private fun getProtocolName(profile: ProfileItem): String {
        return profile.configType.name
    }

    private fun showServerActionsMenu(
        anchor: View,
        guid: String,
        profile: ProfileItem
    ) {
        PopupMenu(anchor.context, anchor).apply {
            menuInflater.inflate(R.menu.menu_server_item_overflow, menu)
            setForceShowIcon(true)
            setOnMenuItemClickListener { item ->
                val position = data.indexOfFirst { it.guid == guid }
                if (position < 0) return@setOnMenuItemClickListener false
                when (item.itemId) {
                    R.id.action_share_server -> {
                        adapterListener?.onShare(guid, profile, position, false)
                        true
                    }

                    R.id.action_edit_server -> {
                        adapterListener?.onEdit(guid, position, profile)
                        true
                    }

                    R.id.action_remove_server -> {
                        adapterListener?.onRemove(guid, position)
                        true
                    }

                    else -> false
                }
            }
            show()
        }
    }

    private fun getPolicyGroupSubText(context: android.content.Context, profile: ProfileItem): String {
        val subId = profile.policyGroupSubscriptionId
        if (subId.isNullOrEmpty()) {
            return context.getString(R.string.filter_config_all)
        }
        val sub = MmkvManager.decodeSubscriptions().firstOrNull { it.guid == subId }
        val name = sub?.subscription?.remarks?.takeIf { it.isNotBlank() }
        return name ?: subId
    }

    private fun bindNetworkSecurity(
        holder: MainViewHolder,
        profile: ProfileItem,
        enabled: Boolean
    ) {
        val context = holder.itemView.context
        val iconSize = (14 * context.resources.displayMetrics.density).toInt()

        fun makeIcon(drawableRes: Int): android.graphics.drawable.Drawable? {
            val d = ContextCompat.getDrawable(context, drawableRes) ?: return null
            val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(d.mutate())
            androidx.core.graphics.drawable.DrawableCompat.setTint(
                wrapped,
                context.getColorAttr("colorOnSurfaceVariant")
            )
            wrapped.setBounds(0, 0, iconSize, iconSize)
            return wrapped
        }

        val isComplex = profile.configType.isComplexType() && profile.configType != EConfigType.CUSTOM
        val isPolicyGroup = profile.configType == EConfigType.POLICYGROUP
        val network = profile.network?.takeIf { it.isNotBlank() }

        val policyGroupTypeLabel = if (isPolicyGroup) {
            val typePos = profile.policyGroupType?.toIntOrNull() ?: 0
            context.resources.getStringArray(R.array.policy_group_type)
            .getOrNull(typePos)
            ?.lowercase()
        } else {
            null
        }

        val security = profile.security?.takeIf { it.isNotBlank() }?.let { sec ->
            if (profile.insecure == true && sec.equals("tls", ignoreCase = true)) {
                "$sec(insecure)"
            } else {
                sec
            }
        } ?: "none"

        val showAny = enabled && (isPolicyGroup || !isComplex)
        holder.views.layoutNetworkSecurity.visibility =
        if (showAny) View.VISIBLE else View.GONE

        if (enabled && isPolicyGroup && policyGroupTypeLabel != null) {
            holder.views.tvNetwork.text = policyGroupTypeLabel
            holder.views.tvNetwork.setCompoundDrawables(makeIcon(RemixR.drawable.rmx_system_thumb_up_line), null, null, null)
            holder.views.tvNetwork.visibility = View.VISIBLE
        } else if (enabled && !isComplex && network != null) {
            holder.views.tvNetwork.text = network
            holder.views.tvNetwork.setCompoundDrawables(makeIcon(RemixR.drawable.rmx_system_thumb_up_line), null, null, null)
            holder.views.tvNetwork.visibility = View.VISIBLE
        } else {
            holder.views.tvNetwork.visibility = View.GONE
        }

        if (enabled && !isComplex) {
            holder.views.tvSecurity.text = security
            val iconRes = if (security == "none") RemixR.drawable.rmx_lock_unlock_line else RemixR.drawable.rmx_lock_line
            holder.views.tvSecurity.setCompoundDrawables(makeIcon(iconRes), null, null, null)
            holder.views.tvSecurity.visibility = View.VISIBLE
        } else {
            holder.views.tvSecurity.visibility = View.GONE
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun removeServerSub(guid: String, position: Int) {
        val idx = data.indexOfFirst { it.guid == guid }
        if (idx >= 0) {
            data.removeAt(idx)
            if (isGridMode) {
                notifyDataSetChanged()
            } else {
                notifyItemRemoved(idx)
                notifyItemRangeChanged(idx, data.size - idx)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setSelectServer(fromPosition: Int, toPosition: Int) {
        if (isGridMode) {
            notifyDataSetChanged()
        } else {
            if (fromPosition in data.indices) notifyItemChanged(fromPosition)
            if (toPosition in data.indices) notifyItemChanged(toPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM_LIST ->
            MainViewHolder(
                ListItemViews(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            )
            VIEW_TYPE_ITEM_GRID ->
            MainViewHolder(
                GridItemViews(ItemRecyclerMainGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            )
            else ->
            FooterViewHolder(ItemRecyclerFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            position == data.size -> VIEW_TYPE_FOOTER
            isGridMode -> VIEW_TYPE_ITEM_GRID
            else -> VIEW_TYPE_ITEM_LIST
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), ItemTouchHelperViewHolder {
        override fun onItemSelected() {}
        override fun onItemClear() {}
    }

    interface MainItemViews {
        val root: View
        val isGridItem: Boolean
        val layoutCard: com.google.android.material.card.MaterialCardView
        val layoutIndicator: View?
        val infoContainer: View
        val tvName: android.widget.TextView
        val vStatusDot: View
        val ivPinIndicator: android.widget.ImageView
        val tvType: com.google.android.material.chip.Chip
        val layoutSubscription: View
        val tvSubscription: com.google.android.material.chip.Chip
        val layoutMore: View?
        val layoutShare: View?
        val layoutEdit: View?
        val layoutRemove: View?
        val tvStatistics: android.widget.TextView
        val tvTestResult: android.widget.TextView
        val tvCountryCode: android.widget.TextView
        val layoutTestMetadata: View?
        val layoutNetworkSecurity: View
        val tvNetwork: android.widget.TextView
        val tvSecurity: android.widget.TextView
        val tvTraffic: android.widget.TextView
    }

    private class ListItemViews(private val b: ItemRecyclerMainBinding) : MainItemViews {
        override val root get() = b.root
        override val isGridItem = false
        override val layoutCard get() = b.layoutCard
        override val layoutIndicator get() = b.layoutIndicator
        override val infoContainer get() = b.infoContainer
        override val tvName get() = b.tvName
        override val vStatusDot get() = b.vStatusDot
        override val ivPinIndicator get() = b.ivPinIndicator
        override val tvType get() = b.tvType
        override val layoutSubscription get() = b.layoutSubscription
        override val tvSubscription get() = b.tvSubscription
        override val layoutMore get() = b.layoutMore
        override val layoutShare get() = b.layoutShare
        override val layoutEdit get() = b.layoutEdit
        override val layoutRemove get() = b.layoutRemove
        override val tvStatistics get() = b.tvStatistics
        override val tvTestResult get() = b.tvTestResult
        override val tvCountryCode get() = b.tvCountryCode
        override val layoutTestMetadata: View? = null
        override val layoutNetworkSecurity get() = b.layoutNetworkSecurity
        override val tvNetwork get() = b.tvNetwork
        override val tvSecurity get() = b.tvSecurity
        override val tvTraffic get() = b.tvTraffic
    }

    private class GridItemViews(private val b: ItemRecyclerMainGridBinding) : MainItemViews {
        override val root get() = b.root
        override val isGridItem = true
        override val layoutCard get() = b.layoutCard
        override val layoutIndicator: View? = null
        override val infoContainer get() = b.infoContainer
        override val tvName get() = b.tvName
        override val vStatusDot get() = b.vStatusDot
        override val ivPinIndicator get() = b.ivPinIndicator
        override val tvType get() = b.tvType
        override val layoutSubscription get() = b.layoutSubscription
        override val tvSubscription get() = b.tvSubscription
        override val layoutMore get() = b.layoutMore
        override val layoutShare: View? = null
        override val layoutEdit: View? = null
        override val layoutRemove: View? = null
        override val tvStatistics get() = b.tvStatistics
        override val tvTestResult get() = b.tvTestResult
        override val tvCountryCode get() = b.tvCountryCode
        override val layoutTestMetadata get() = b.layoutTestMetadata
        override val layoutNetworkSecurity get() = b.layoutNetworkSecurity
        override val tvNetwork get() = b.tvNetwork
        override val tvSecurity get() = b.tvSecurity
        override val tvTraffic get() = b.tvTraffic
    }

    class MainViewHolder(val views: MainItemViews) :
    BaseViewHolder(views.root) {
        override fun onItemSelected() {
            val context = itemView.context
            views.layoutCard.setCardBackgroundColor(context.getColorAttr("colorCard"))
        }
        override fun onItemClear() {
            val context = itemView.context
            views.layoutCard.setCardBackgroundColor(context.getColorAttr("colorCard"))
        }
    }

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
    BaseViewHolder(itemFooterBinding.root)

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mainViewModel.swapServer(fromPosition, toPosition)
        if (fromPosition < data.size && toPosition < data.size) {
            Collections.swap(data, fromPosition, toPosition)
        }
        if (isGridMode) {
            notifyDataSetChanged()
        } else {
            notifyItemMoved(fromPosition, toPosition)
        }
        return true
    }

    override fun onItemMoveCompleted() {}
    override fun onItemDismiss(position: Int) {}
}
