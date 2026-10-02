package com.miku.ray.ui.routing

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.R
import com.miku.ray.contracts.BaseAdapterListener
import com.miku.ray.databinding.ItemRecyclerRoutingSettingBinding
import com.miku.ray.helper.ItemTouchHelperAdapter
import com.miku.ray.helper.ItemTouchHelperViewHolder
import com.miku.ray.util.getColorAttr
import java.util.Collections

class RoutingSettingRecyclerAdapter(
    private val viewModel: RoutingSettingsViewModel,
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<RoutingSettingRecyclerAdapter.MainViewHolder>(),
ItemTouchHelperAdapter {

    private var data = viewModel.getAll().toMutableList()

    @Deprecated("Collect viewModel.rulesets instead, so remove/update/reload are reflected immediately without a manual notify call that can desync from this adapter's own backing list.")
    fun syncFromViewModel() {
        submitData(viewModel.getAll())
    }

    fun submitData(newData: List<com.miku.ray.dto.entities.RulesetItem>) {
        data = newData.toMutableList()
        notifyDataSetChanged()
    }

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        val ruleset = data[position]

        holder.itemRoutingSettingBinding.remarks.text = ruleset.remarks
        holder.itemRoutingSettingBinding.domainIp.text = (ruleset.domain ?: ruleset.ip ?: ruleset.process ?: ruleset.port)?.toString()
        holder.itemRoutingSettingBinding.outboundTag.text = ruleset.outboundTag
        holder.itemRoutingSettingBinding.chkEnable.isChecked = ruleset.enabled

        holder.itemRoutingSettingBinding.imgLocked.isVisible = ruleset.locked == true

        holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        holder.itemRoutingSettingBinding.layoutEdit.setOnClickListener {
            adapterListener?.onEdit(ruleset.id, holder.bindingAdapterPosition)
        }

        holder.itemRoutingSettingBinding.layoutRemove.setOnClickListener {
            adapterListener?.onRemove(ruleset.id, holder.bindingAdapterPosition)
        }

        holder.itemRoutingSettingBinding.chkEnable.setOnCheckedChangeListener { it, isChecked ->
            if (!it.isPressed) return@setOnCheckedChangeListener
            ruleset.enabled = isChecked
            viewModel.update(position, ruleset)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerRoutingSettingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    class MainViewHolder(val itemRoutingSettingBinding: ItemRecyclerRoutingSettingBinding) :
    BaseViewHolder(itemRoutingSettingBinding.root), ItemTouchHelperViewHolder {

        override fun onItemSelected() {
            val context = itemView.context
            itemRoutingSettingBinding.layoutCard.setCardBackgroundColor(context.getColorAttr("colorCard"))
        }

        override fun onItemClear() {
            val context = itemView.context
            itemRoutingSettingBinding.layoutCard.setCardBackgroundColor(context.getColorAttr("colorCard"))
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), ItemTouchHelperViewHolder {
        override fun onItemSelected() {}
        override fun onItemClear() {}
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition !in data.indices || toPosition !in data.indices) return false
        viewModel.swap(fromPosition, toPosition)
        Collections.swap(data, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
    }

    override fun onItemDismiss(position: Int) {
    }
}
