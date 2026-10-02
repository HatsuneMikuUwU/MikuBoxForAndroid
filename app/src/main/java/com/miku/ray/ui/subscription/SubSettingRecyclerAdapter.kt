package com.miku.ray.ui.subscription

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.contracts.BaseAdapterListener
import com.miku.ray.R
import com.miku.ray.databinding.ItemRecyclerSubSettingBinding
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.dto.entities.SubscriptionItem
import com.miku.ray.handler.MmkvManager
import com.miku.ray.helper.ItemTouchHelperAdapter
import com.miku.ray.helper.ItemTouchHelperViewHolder
import com.miku.ray.util.Utils
import java.text.DateFormat
import java.util.Collections
import java.util.Date

class SubSettingRecyclerAdapter(
    private val viewModel: SubscriptionsViewModel,
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<SubSettingRecyclerAdapter.MainViewHolder>(), ItemTouchHelperAdapter {

    private var data = viewModel.getAll().toMutableList()

    @Deprecated("Collect viewModel.subscriptionsState instead, so updates from anywhere (drag reorder, edit, remove) are reflected immediately without waiting for onResume.")
    fun syncFromViewModel() {
        submitData(viewModel.getAll())
    }

    fun submitData(newData: List<SubscriptionCache>) {
        data = newData.toMutableList()
        notifyDataSetChanged()
    }

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val subId = data[position].guid
        val subItem = data[position].subscription
        holder.itemSubSettingBinding.tvName.text = subItem.remarks
        holder.itemSubSettingBinding.tvUrl.text = subItem.url.ifEmpty {
            holder.itemView.context.getString(R.string.sub_setting_no_url)
        }
        holder.itemSubSettingBinding.chkEnable.isChecked = subItem.enabled
        val serverCount = MmkvManager.decodeServerList(subId).size
        holder.itemSubSettingBinding.tvServerCount.text = holder.itemView.context.resources.getQuantityString(
            R.plurals.sub_setting_server_count, serverCount, serverCount
        )
        val lastUpdatedText = if (TextUtils.isEmpty(subItem.url)) {
            Utils.formatTimestamp(subItem.addedTime)
        } else {
            Utils.formatTimestamp(subItem.lastUpdated)
        }
        holder.itemSubSettingBinding.tvLastUpdated.text = lastUpdatedText
        val usageText = formatSubscriptionUsage(holder.itemView.context, subId, subItem)
        val expiryText = formatSubscriptionExpiry(holder.itemView.context, subItem)
        holder.itemSubSettingBinding.tvSubscriptionUsage.text = usageText
        holder.itemSubSettingBinding.tvSubscriptionExpire.text = expiryText
        holder.itemSubSettingBinding.tvLastUpdated.visibility =
        if (lastUpdatedText.isEmpty()) View.GONE else View.VISIBLE
        holder.itemSubSettingBinding.tvSubscriptionUsage.visibility =
        if (usageText == null) View.GONE else View.VISIBLE
        holder.itemSubSettingBinding.tvSubscriptionExpire.visibility =
        if (expiryText == null) View.GONE else View.VISIBLE
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        holder.itemSubSettingBinding.layoutEdit.setOnClickListener {
            adapterListener?.onEdit(subId, position)
        }

        holder.itemSubSettingBinding.layoutRemove.setOnClickListener {
            adapterListener?.onRemove(subId, position)
        }

        holder.itemSubSettingBinding.chkEnable.setOnCheckedChangeListener { it, isChecked ->
            if (!it.isPressed) return@setOnCheckedChangeListener
            subItem.enabled = isChecked
            viewModel.update(subId, subItem)
        }

        holder.itemSubSettingBinding.tvUrl.visibility = View.VISIBLE
        if (TextUtils.isEmpty(subItem.url)) {
            holder.itemSubSettingBinding.layoutShare.visibility = View.GONE
            holder.itemSubSettingBinding.chkEnable.visibility = View.GONE
            holder.itemSubSettingBinding.tvSubscriptionExpire.visibility = View.GONE
        } else {
            holder.itemSubSettingBinding.layoutShare.visibility = View.VISIBLE
            holder.itemSubSettingBinding.chkEnable.visibility = View.VISIBLE
            holder.itemSubSettingBinding.tvSubscriptionExpire.visibility =
            if (expiryText == null) View.GONE else View.VISIBLE
            holder.itemSubSettingBinding.layoutShare.setOnClickListener {
                adapterListener?.onShare(subItem.url)
            }
        }
    }

    private fun formatSubscriptionUsage(context: Context, subId: String, subscription: SubscriptionItem): String? {
        return when {
            subscription.bytesUsed >= 0L && subscription.bytesRemaining >= 0L -> {
                context.getString(
                    R.string.sub_setting_usage,
                    MmkvManager.formatTrafficBytesPublic(subscription.bytesUsed),
                    MmkvManager.formatTrafficBytesPublic(subscription.bytesRemaining),
                )
            }
            subscription.bytesUsed >= 0L -> {
                context.getString(
                    R.string.sub_setting_usage_used,
                    MmkvManager.formatTrafficBytesPublic(subscription.bytesUsed),
                )
            }
            else -> {
                MmkvManager.getGroupTrafficString(subId)?.let { groupTraffic ->
                    context.getString(R.string.sub_setting_usage_used, groupTraffic)
                }
            }
        }
    }

    private fun formatSubscriptionExpiry(context: Context, subscription: SubscriptionItem): String? {
        return subscription.expiresAt
        .takeIf { it in 1L..(Long.MAX_VALUE / 1000L) }
        ?.let { seconds ->
            context.getString(
                R.string.sub_setting_expire,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(seconds * 1000L)),
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerSubSettingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    class MainViewHolder(val itemSubSettingBinding: ItemRecyclerSubSettingBinding) :
    BaseViewHolder(itemSubSettingBinding.root), ItemTouchHelperViewHolder

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.TRANSPARENT)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition !in data.indices || toPosition !in data.indices) return false
        viewModel.swap(fromPosition, toPosition)
        Collections.swap(data, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        viewModel.commitOrder()
    }

    override fun onItemDismiss(position: Int) {
    }
}
