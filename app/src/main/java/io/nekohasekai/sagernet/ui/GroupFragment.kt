package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.view.LayoutInflater
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.*
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifOptions
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.*
import io.nekohasekai.sagernet.databinding.LayoutGroupItemBinding
import io.nekohasekai.sagernet.fmt.toUniversalLink
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.widget.ListListener
import io.nekohasekai.sagernet.widget.QRCodeDialog
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.delay
import moe.matsuri.nb4a.utils.Util
import moe.matsuri.nb4a.utils.toBytesString
import java.lang.NumberFormatException
import java.util.*
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.widget.StatsBar
import io.nekohasekai.sagernet.utils.showBlur
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.ui.bottomsheet.GroupMenuBottomSheet
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.widget.TextView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.R as MaterialR
import io.nekohasekai.sagernet.ui.toolbar.GroupMenuController
import kotlin.math.abs

class GroupFragment : ToolbarFragment(R.layout.layout_group), 
    GroupMenuBottomSheet.OnOptionClickListener {

    lateinit var activity: MainActivity
    lateinit var groupListView: RecyclerView
    lateinit var layoutManager: LinearLayoutManager
    lateinit var groupAdapter: GroupAdapter
    lateinit var undoManager: UndoSnackbarManager<ProxyGroup>
    
    private lateinit var menuController: GroupMenuController
    private var summarySubsView: TextView? = null
    private var summaryNodesView: TextView? = null
    private var summaryAlertView: TextView? = null

    private enum class SubHealth { OK, WARN, ERR, LOCAL }

    private data class SubMetrics(
        val hasQuota: Boolean = false,
        val usedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val percent: Int = 0,
        val daysLeft: Int? = null,
        val health: SubHealth = SubHealth.LOCAL
    )

    private fun parseSubMetrics(group: ProxyGroup): SubMetrics {
        if (group.type != GroupType.SUBSCRIPTION) return SubMetrics(health = SubHealth.LOCAL)
        val sub = group.subscription ?: return SubMetrics(health = SubHealth.OK)

        var used = 0L
        var total = 0L
        var expireSec = 0L

        if (sub.bytesUsed > 0L || sub.bytesRemaining > 0L) {
            used = sub.bytesUsed.coerceAtLeast(0L)
            val remain = sub.bytesRemaining.coerceAtLeast(0L)
            total = if (remain > 0L) used + remain else 0L
            if (sub.expiryDate != null && sub.expiryDate > 0) {
                expireSec = sub.expiryDate.toLong()
            }
        } else if (!sub.subscriptionUserinfo.isNullOrBlank()) {
            val info = sub.subscriptionUserinfo
            fun extract(regex: String): Long? =
                regex.toRegex(RegexOption.IGNORE_CASE).find(info)?.groupValues?.getOrNull(1)?.toLongOrNull()
            used = (extract("upload=([0-9]+)") ?: 0L) + (extract("download=([0-9]+)") ?: 0L)
            total = extract("total=([0-9]+)") ?: 0L
            expireSec = extract("expire=([0-9]+)") ?: 0L
        }

        val hasQuota = used > 0L || total > 0L
        val pct = if (total > 0L) ((used * 100L) / total).toInt().coerceIn(0, 100) else 0
        val daysLeft: Int? = if (expireSec > 0L) {
            val nowSec = System.currentTimeMillis() / 1000L
            val diffSec = expireSec - nowSec
            if (diffSec <= 0L) 0 else ((diffSec + 86399L) / 86400L).toInt()
        } else null

        val health = when {
            (daysLeft != null && daysLeft <= 0) || (total > 0L && used >= total) -> SubHealth.ERR
            (daysLeft != null && daysLeft <= 5) || (total > 0L && pct >= 80) -> SubHealth.WARN
            else -> SubHealth.OK
        }

        return SubMetrics(hasQuota, used, total, pct, daysLeft, health)
    }

    private fun refreshHeroSummary() {
        if (!isAdded || !::groupAdapter.isInitialized) return
        val snapshot = groupAdapter.groupList.toList()
        runOnDefaultDispatcher {
            var subCount = 0
            var totalNodes = 0L
            var attentionCount = 0
            for (g in snapshot) {
                totalNodes += SagerDatabase.proxyDao.countByGroup(g.id)
                if (g.type == GroupType.SUBSCRIPTION) {
                    subCount++
                    val h = parseSubMetrics(g).health
                    if (h == SubHealth.WARN || h == SubHealth.ERR) attentionCount++
                }
            }
            onMainDispatcher {
                if (!isAdded) return@onMainDispatcher
                summarySubsView?.text = getString(R.string.sub_summary_count, subCount)
                summaryNodesView?.text = getString(R.string.sub_summary_nodes, totalNodes.toInt())
                summaryAlertView?.apply {
                    if (attentionCount > 0) {
                        visibility = View.VISIBLE
                        text = getString(R.string.sub_summary_attention, attentionCount)
                        backgroundTintList = ColorStateList.valueOf(Color.argb(235, 255, 255, 255))
                    } else {
                        visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun formatRelativeSync(lastUpdatedSec: Int): String {
        if (lastUpdatedSec <= 0) return getString(R.string.sub_local_group)
        val diffMin = ((System.currentTimeMillis() / 1000L) - lastUpdatedSec.toLong()).coerceAtLeast(0L) / 60L
        return when {
            diffMin < 2L -> getString(R.string.sub_synced_just_now)
            diffMin < 60L -> getString(R.string.sub_synced_min_ago, diffMin.toInt())
            diffMin < 1440L -> getString(R.string.sub_synced_hour_ago, (diffMin / 60L).toInt())
            else -> getString(R.string.sub_synced_day_ago, (diffMin / 1440L).toInt())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity = requireActivity() as MainActivity

        ViewCompat.setOnApplyWindowInsetsListener(view, ListListener)

        val appBar = view.findViewById<AppBarLayout>(R.id.appbar)
        val heroTextContainer = view.findViewById<View>(R.id.hero_text_container)
        val toolbarView = view.findViewById<Toolbar>(R.id.toolbar)
        summarySubsView = view.findViewById(R.id.tv_summary_subs)
        summaryNodesView = view.findViewById(R.id.tv_summary_nodes)
        summaryAlertView = view.findViewById(R.id.tv_summary_alert)

        toolbarView.title = ""
        appBar?.addOnOffsetChangedListener { layout, verticalOffset ->
            val range = layout.totalScrollRange.coerceAtLeast(1)
            val progress = abs(verticalOffset).toFloat() / range.toFloat()
            heroTextContainer?.alpha = (1f - progress * 1.4f).coerceIn(0f, 1f)
            toolbarView.title = if (progress > 0.72f) getString(R.string.menu_group) else ""
        }

        toolbar = toolbarView
        
        menuController = GroupMenuController(
            toolbar = toolbar, 
            fragmentManager = childFragmentManager,
            listener = this
        )

        groupListView = view.findViewById(R.id.group_list)
        layoutManager = FixedLinearLayoutManager(groupListView)
        groupListView.layoutManager = layoutManager
        groupAdapter = GroupAdapter()
        GroupManager.addListener(groupAdapter)
        groupListView.adapter = groupAdapter

        undoManager = UndoSnackbarManager(activity, groupAdapter)

        view.post {
            val bottomAppBar = requireActivity().findViewById<StatsBar>(R.id.stats) ?: return@post

            fun updateBottomBarVisibility() {
                val isConnected = DataStore.serviceState == BaseService.State.Connected
                val showController = DataStore.showBottomBar

                if (!isConnected) {
                    bottomAppBar.performHide()
                } else {
                    if (showController) bottomAppBar.performShow()
                    else bottomAppBar.performHide()
                }
            }

            updateBottomBarVisibility()

            if (groupListView != null) {
                ViewCompat.setNestedScrollingEnabled(groupListView, true)

                groupListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)

                        val isConnected = DataStore.serviceState == BaseService.State.Connected
                        val showController = DataStore.showBottomBar

                        if (isConnected && showController) {
                            if (dy > 6) bottomAppBar.performHide()
                            else if (dy < -6) bottomAppBar.performShow()
                        } else {
                            bottomAppBar.performHide()
                        }
                    }
                })
            }
        }

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START
        ) {
            override fun getSwipeDirs(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ): Int {
                val proxyGroup = (viewHolder as GroupHolder).proxyGroup
                if (proxyGroup.ungrouped || proxyGroup.id in GroupUpdater.updating) {
                    return 0
                }
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun getDragDirs(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ): Int {
                val proxyGroup = (viewHolder as GroupHolder).proxyGroup
                if (proxyGroup.ungrouped || proxyGroup.id in GroupUpdater.updating) {
                    return 0
                }
                return super.getDragDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val index = viewHolder.bindingAdapterPosition
                groupAdapter.remove(index)
                undoManager.remove(index to (viewHolder as GroupHolder).proxyGroup)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder,
            ): Boolean {
                groupAdapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) {
                super.clearView(recyclerView, viewHolder)
                groupAdapter.commitMove()
            }
        }).attachToRecyclerView(groupListView)
    }

    override fun onResume() {
        super.onResume()
        if (::menuController.isInitialized) {
            menuController.refresh()
        }
    }

    override fun onOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.action_new_group -> {
                startActivity(Intent(context, GroupSettingsActivity::class.java))
            }

            R.id.action_update_all -> {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                    .setMessage(R.string.update_all_subscription)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        SagerDatabase.groupDao.allGroups()
                            .filter { it.type == GroupType.SUBSCRIPTION }
                            .forEach {
                                GroupUpdater.startUpdate(it, true)
                            }
                    }
                    .setNegativeButton(R.string.no, null)
                    .showBlur()
            }
        }
    }

    private lateinit var selectedGroup: ProxyGroup

    private val exportProfiles =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { data ->
            if (data != null) {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(selectedGroup.id)
                    val links = profiles.joinToString("\n") { it.toStdLink(compact = true) }
                    try {
                        (requireActivity() as MainActivity).contentResolver.openOutputStream(
                            data
                        )!!.bufferedWriter().use {
                            it.write(links)
                        }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }

                }
            }
        }

    inner class GroupAdapter : RecyclerView.Adapter<GroupHolder>(),
        GroupManager.Listener,
        UndoSnackbarManager.Interface<ProxyGroup> {

        val groupList = ArrayList<ProxyGroup>()

        suspend fun reload() {
            val groups = SagerDatabase.groupDao.allGroups().toMutableList()
            if (groups.size > 1 && SagerDatabase.proxyDao.countByGroup(groups.find { it.ungrouped }!!.id) == 0L) groups.removeAll { it.ungrouped }
            groupList.clear()
            groupList.addAll(groups)
            groupListView.post {
                notifyDataSetChanged()
                refreshHeroSummary()
            }
        }

        init {
            setHasStableIds(true)

            runOnDefaultDispatcher {
                reload()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupHolder {
            return GroupHolder(LayoutGroupItemBinding.inflate(layoutInflater, parent, false))
        }

        override fun onBindViewHolder(holder: GroupHolder, position: Int) {
            holder.bind(groupList[position])
        }

        override fun getItemCount(): Int {
            return groupList.size
        }

        override fun getItemId(position: Int): Long {
            return groupList[position].id
        }

        private val updated = HashSet<ProxyGroup>()

        fun move(from: Int, to: Int) {
            val first = groupList[from]
            var previousOrder = first.userOrder
            val (step, range) = if (from < to) Pair(1, from until to) else Pair(
                -1, to + 1 downTo from
            )
            for (i in range) {
                val next = groupList[i + step]
                val order = next.userOrder
                next.userOrder = previousOrder
                previousOrder = order
                groupList[i] = next
                updated.add(next)
            }
            first.userOrder = previousOrder
            groupList[to] = first
            updated.add(first)
            notifyItemMoved(from, to)
        }

        fun commitMove() = runOnDefaultDispatcher {
            updated.forEach { SagerDatabase.groupDao.updateGroup(it) }
            updated.clear()
        }

        fun remove(index: Int) {
            groupList.removeAt(index)
            notifyItemRemoved(index)
        }

        override fun undo(actions: List<Pair<Int, ProxyGroup>>) {
            for ((index, item) in actions) {
                groupList.add(index, item)
                notifyItemInserted(index)
            }
        }

        override fun commit(actions: List<Pair<Int, ProxyGroup>>) {
            val groups = actions.map { it.second }
            runOnDefaultDispatcher {
                GroupManager.deleteGroup(groups)
                reload()
            }
        }

        override suspend fun groupAdd(group: ProxyGroup) {
            groupList.add(group)
            delay(300L)

            onMainDispatcher {
                undoManager.flush()
                notifyItemInserted(groupList.size - 1)
                refreshHeroSummary()

                if (group.type == GroupType.SUBSCRIPTION) {
                    GroupUpdater.startUpdate(group, true)
                }
            }
        }

        override suspend fun groupRemoved(groupId: Long) {
            val index = groupList.indexOfFirst { it.id == groupId }
            if (index == -1) return
            onMainDispatcher {
                undoManager.flush()
                if (SagerDatabase.groupDao.allGroups().size <= 2) {
                    runOnDefaultDispatcher {
                        reload()
                    }
                } else {
                    groupList.removeAt(index)
                    notifyItemRemoved(index)
                    refreshHeroSummary()
                }
            }
        }

        override suspend fun groupUpdated(group: ProxyGroup) {
            val index = groupList.indexOfFirst { it.id == group.id }
            if (index == -1) {
                reload()
                return
            }
            groupList[index] = group
            onMainDispatcher {
                undoManager.flush()

                notifyItemChanged(index)
                refreshHeroSummary()
            }
        }

        override suspend fun groupUpdated(groupId: Long) {
            val index = groupList.indexOfFirst { it.id == groupId }
            if (index == -1) {
                reload()
                return
            }
            onMainDispatcher {
                notifyItemChanged(index)
                refreshHeroSummary()
            }
        }
    }

    override fun onDestroy() {
        if (::groupAdapter.isInitialized) {
            GroupManager.removeListener(groupAdapter)
        }

        super.onDestroy()

        if (!::undoManager.isInitialized) return
        undoManager.flush()
    }

    inner class GroupHolder(binding: LayoutGroupItemBinding) :
        RecyclerView.ViewHolder(binding.root),
        PopupMenu.OnMenuItemClickListener {

        lateinit var proxyGroup: ProxyGroup
        val groupName = binding.groupName
        val groupStatus = binding.groupStatus
        val groupTraffic = binding.groupTraffic
        val groupUser = binding.groupUser
        val editButton = binding.edit
        val optionsButton = binding.options
        val updateButton = binding.groupUpdate
        val subscriptionUpdateProgress = binding.subscriptionUpdateProgress
        val statusDot = binding.statusDot
        val statusDotHalo = binding.statusDotHalo
        val statusPill = binding.statusPill
        val usagePanel = binding.usagePanel
        val tvNodesPct = binding.tvNodesPct
        val usageProgress = binding.usageProgress
        
        private val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"

        fun export(link: String) {
            val success = SagerNet.trySetPrimaryClip(link)
            activity.snackbar(if (success) R.string.action_export_msg else R.string.action_export_err)
                .show()
        }

        fun exportClipboard() {
            runOnDefaultDispatcher {
                val profiles = SagerDatabase.proxyDao.getByGroup(selectedGroup.id)
                val links = profiles.joinToString("\n") { it.toStdLink(compact = true) }
                onMainDispatcher {
                    SagerNet.trySetPrimaryClip(links)
                    snackbar(getString(R.string.copy_toast_msg)).show()
                }
            }
        }

        fun exportFile() {
             startFilesForResult(exportProfiles, "profiles_${proxyGroup.displayName()}.txt")
        }

        fun clearGroup() {
             MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                .setMessage(R.string.clear_profiles_message)
                .setPositiveButton(R.string.yes) { _, _ ->
                    runOnDefaultDispatcher {
                        GroupManager.clearGroup(proxyGroup.id)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .showBlur()
        }

        private fun showPopupMenu(anchor: View) {
            val popup = PopupMenu(requireContext(), anchor)
            popup.menuInflater.inflate(R.menu.group_action_menu, popup.menu)

            if (proxyGroup.type != GroupType.SUBSCRIPTION) {
                popup.menu.removeItem(R.id.action_share_subscription)
            }
            popup.setOnMenuItemClickListener(this)
            popup.show()
        }

        private fun showBottomSheet() {
            val context = itemView.context
            val dialog = BottomSheetDialog(context)
            
            Theme.applyWindowBlur(dialog.window)
            
            val sheetView = LayoutInflater.from(context).inflate(R.layout.uwu_bottom_sheet_share_group, null)
            dialog.setContentView(sheetView)
            
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true

            val btnClear = sheetView.findViewById<View>(R.id.btn_clear)
            val btnSub = sheetView.findViewById<View>(R.id.btn_share_sub) // Used for Group Share
            val btnExport = sheetView.findViewById<View>(R.id.btn_export_config) // Used for Export File/Clip
            val bannerImageView = sheetView.findViewById<ImageView>(R.id.img_banner_sheet)
            
            if (bannerImageView != null) {
                bannerImageView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                val bannerUriString = DataStore.configurationStore.getString("custom_sheet_banner_uri", null)
                val targetTag = if (bannerUriString.isNullOrBlank()) TAG_SHEET_DEFAULT else bannerUriString
                
                if (bannerImageView.tag != targetTag) {
                    if (!bannerUriString.isNullOrBlank()) {
                        Glide.with(context)
                            .load(Uri.parse(bannerUriString))
                            .downsample(DownsampleStrategy.NONE)
                            .set(GifOptions.DECODE_FORMAT, DecodeFormat.PREFER_ARGB_8888)
                            .format(DecodeFormat.PREFER_ARGB_8888)
                            .override(Target.SIZE_ORIGINAL)
                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                            .skipMemoryCache(false)
                            .error(R.drawable.uwu_banner_image_about)
                            .into(bannerImageView)
                    } else {
                        Glide.with(context).clear(bannerImageView)
                        bannerImageView.setImageResource(R.drawable.uwu_banner_image_about)
                    }
                    bannerImageView.tag = targetTag
                }
            }
            
            val particlesView = sheetView.findViewById<View>(R.id.ParticlesView)
            particlesView?.visibility = if (DataStore.disableParticlesSheet) View.GONE else View.VISIBLE

            if (proxyGroup.type != GroupType.SUBSCRIPTION) {
                 btnSub.visibility = View.GONE
            }

            fun showSubSelection(title: String, options: Array<String>, onClick: (Int) -> Unit) {
                val adapter = android.widget.ArrayAdapter(context, R.layout.layout_dialog_item, options)
                MaterialAlertDialogBuilder(context)
                    .setTitle(title)
                    .setAdapter(adapter) { d, w -> onClick(w); d.dismiss() }
                    .showBlur()
            }

            btnSub.setOnClickListener {
                dialog.dismiss()
                val options = arrayOf(
                    context.getString(R.string.share_qr_nfc),
                    context.getString(R.string.action_export_clipboard)
                )
                showSubSelection(context.getString(R.string.share_subscription), options) { which ->
                     when(which) {
                         0 -> QRCodeDialog(proxyGroup.toUniversalLink(), proxyGroup.displayName()).showAllowingStateLoss(parentFragmentManager)
                         1 -> export(proxyGroup.toUniversalLink())
                     }
                }
            }

            btnExport.setOnClickListener {
                dialog.dismiss()
                val options = arrayOf(
                    context.getString(R.string.action_export_clipboard),
                    context.getString(R.string.action_export_file)
                )
                showSubSelection(context.getString(R.string.action_export), options) { which ->
                    when(which) {
                        0 -> exportClipboard()
                        1 -> exportFile()
                    }
                }
            }
            
            btnClear.setOnClickListener {
                dialog.dismiss()
                clearGroup()
            }
            
            dialog.show()
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_universal_qr -> {
                    QRCodeDialog(
                        proxyGroup.toUniversalLink(), proxyGroup.displayName()
                    ).showAllowingStateLoss(parentFragmentManager)
                    true
                }
                R.id.action_universal_clipboard -> {
                    export(proxyGroup.toUniversalLink())
                    true
                }
                R.id.action_export_clipboard -> {
                    exportClipboard()
                    true
                }
                R.id.action_export_file -> {
                    exportFile()
                    true
                }
                R.id.action_clear -> {
                    clearGroup()
                    true
                }
                else -> false
            }
        }


        fun bind(group: ProxyGroup) {
            proxyGroup = group

            itemView.setOnClickListener { }

            editButton.isGone = proxyGroup.ungrouped
            updateButton.isInvisible = proxyGroup.type != GroupType.SUBSCRIPTION
            groupName.text = proxyGroup.displayName()

            editButton.setOnClickListener {
                startActivity(Intent(it.context, GroupSettingsActivity::class.java).apply {
                    putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, group.id)
                })
            }

            updateButton.setOnClickListener {
                GroupUpdater.startUpdate(proxyGroup, true)
            }

            optionsButton.setOnClickListener {
                selectedGroup = proxyGroup

                if (DataStore.disableBottomSheetHome) {
                    showPopupMenu(it)
                } else {
                    showBottomSheet()
                }
            }

            val isUpdating = proxyGroup.id in GroupUpdater.updating
            if (isUpdating) {
                subscriptionUpdateProgress.isVisible = true
                if (!GroupUpdater.progress.containsKey(proxyGroup.id)) {
                    subscriptionUpdateProgress.isIndeterminate = true
                } else {
                    subscriptionUpdateProgress.isIndeterminate = false
                    GroupUpdater.progress[proxyGroup.id]?.let {
                        subscriptionUpdateProgress.max = it.max
                        subscriptionUpdateProgress.progress = it.progress
                    }
                }
                updateButton.isEnabled = false
                editButton.isGone = true
            } else {
                subscriptionUpdateProgress.isVisible = false
                updateButton.isEnabled = true
                updateButton.isGone = proxyGroup.type != GroupType.SUBSCRIPTION
                editButton.isGone = proxyGroup.ungrouped
            }

            val subscription = proxyGroup.subscription
            val userText = subscription?.username.orEmpty()
            groupUser.isVisible = userText.isNotBlank()
            groupUser.text = userText

            val metrics = parseSubMetrics(proxyGroup)
            val isDark = (itemView.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val primaryColor = itemView.context.getColorAttr(androidx.appcompat.R.attr.colorPrimary)
            val mutedColor = itemView.context.getColorAttr(MaterialR.attr.colorOnSurfaceVariant)
            val (accentColor, pillBg, pillFg) = when (metrics.health) {
                SubHealth.OK -> Triple(
                    primaryColor,
                    itemView.context.getColorAttr(MaterialR.attr.colorSecondaryContainer),
                    itemView.context.getColorAttr(MaterialR.attr.colorOnSecondaryContainer)
                )
                SubHealth.WARN -> Triple(
                    Color.parseColor("#F59E0B"),
                    Color.parseColor(if (isDark) "#3A2A12" else "#FFF3DC"),
                    Color.parseColor(if (isDark) "#FBBF24" else "#A86400")
                )
                SubHealth.ERR -> Triple(
                    Color.parseColor(if (isDark) "#F87171" else "#EF4444"),
                    Color.parseColor(if (isDark) "#3C1919" else "#FEE4E2"),
                    Color.parseColor(if (isDark) "#FCA5A5" else "#B42318")
                )
                SubHealth.LOCAL -> Triple(
                    mutedColor,
                    itemView.context.getColorAttr(MaterialR.attr.colorSurfaceContainer),
                    mutedColor
                )
            }

            statusDot.backgroundTintList = ColorStateList.valueOf(accentColor)
            statusDotHalo.backgroundTintList = ColorStateList.valueOf(accentColor)
            statusPill.backgroundTintList = ColorStateList.valueOf(pillBg)
            statusPill.setTextColor(pillFg)

            if (metrics.hasQuota) {
                usagePanel.isVisible = true
                usageProgress.setIndicatorColor(accentColor)
                usageProgress.setProgressCompat(metrics.percent, false)
                groupTraffic.text = if (metrics.totalBytes > 0L) {
                    "${metrics.usedBytes.toBytesString()} / ${metrics.totalBytes.toBytesString()}"
                } else {
                    getString(R.string.subscription_used, metrics.usedBytes.toBytesString())
                }
            } else {
                usagePanel.isVisible = false
            }

            runOnDefaultDispatcher {
                val size = SagerDatabase.proxyDao.countByGroup(group.id)
                onMainDispatcher {
                    val nodesLabel = getString(R.string.sub_summary_nodes, size.toInt())
                    if (metrics.hasQuota) {
                        tvNodesPct.text = if (metrics.totalBytes > 0L) {
                            "$nodesLabel · ${metrics.percent}%"
                        } else {
                            nodesLabel
                        }
                    }

                    when (group.type) {
                        GroupType.BASIC -> {
                            statusPill.text = nodesLabel
                            groupStatus.text = if (size == 0L) {
                                getString(R.string.group_status_empty)
                            } else {
                                getString(R.string.sub_local_group)
                            }
                        }
                        GroupType.SUBSCRIPTION -> {
                            statusPill.text = when {
                                metrics.daysLeft == null -> nodesLabel
                                metrics.daysLeft <= 0 -> getString(R.string.sub_expired)
                                else -> getString(R.string.sub_days_left, metrics.daysLeft)
                            }
                            groupStatus.text = if (size == 0L) {
                                getString(R.string.group_status_empty_subscription)
                            } else {
                                formatRelativeSync(group.subscription?.lastUpdated ?: 0)
                            }
                        }
                    }
                }
            }

        }
    }
}
