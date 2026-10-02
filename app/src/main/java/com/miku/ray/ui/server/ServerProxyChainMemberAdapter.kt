package com.miku.ray.ui.server

import android.graphics.Color
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.contracts.BaseAdapterListener
import com.miku.ray.databinding.ItemRecyclerProxyChainMemberBinding
import com.miku.ray.helper.ItemTouchHelperAdapter
import com.miku.ray.helper.ItemTouchHelperViewHolder
import java.util.Collections

class ServerProxyChainMemberAdapter(
    private val members: MutableList<String>,
    private val suggestions: List<String>,
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<ServerProxyChainMemberAdapter.MemberViewHolder>(), ItemTouchHelperAdapter {

    override fun getItemCount(): Int = members.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        return MemberViewHolder(
            ItemRecyclerProxyChainMemberBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val value = members[position]
        holder.binding.tvMemberIndex.text = (position + 1).toString()

        val dropdownAdapter = ArrayAdapter(
            holder.itemView.context,
            android.R.layout.simple_dropdown_item_1line,
            suggestions
        )
        holder.binding.spMemberRemark.setAdapter(dropdownAdapter)
        holder.binding.spMemberRemark.threshold = 0

        holder.binding.spMemberRemark.setText(value, false)

        holder.binding.spMemberRemark.setOnItemClickListener { _, _, selectedIndex, _ ->
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION && adapterPos in members.indices) {
                val selectedItem = dropdownAdapter.getItem(selectedIndex) ?: ""
                members[adapterPos] = selectedItem.trim()
                adapterListener?.onRefreshData()
            }
        }

        holder.binding.spMemberRemark.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@OnFocusChangeListener
            val text = holder.binding.spMemberRemark.text?.toString().orEmpty().trim()
            val adapterPos = holder.bindingAdapterPosition

            if (adapterPos != RecyclerView.NO_POSITION && adapterPos in members.indices && members[adapterPos] != text) {
                members[adapterPos] = text
                adapterListener?.onRefreshData()
            }
        }

        holder.itemView.setOnClickListener {
            holder.binding.spMemberRemark.requestFocus()
            holder.binding.spMemberRemark.showDropDown()
        }

        holder.binding.layoutRemove.setOnClickListener {
            val removePos = holder.bindingAdapterPosition
            if (removePos != RecyclerView.NO_POSITION) {
                adapterListener?.onRemove("", removePos)
            }
        }
    }

    fun addRow() {
        members.add("")
        notifyItemInserted(members.lastIndex)
        adapterListener?.onRefreshData()
    }

    fun removeRow(position: Int) {
        if (position < 0 || position >= members.size) return
        members.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, members.size - position)
        adapterListener?.onRefreshData()
    }

    fun setRemark(position: Int, remark: String) {
        if (position < 0 || position >= members.size) return
        members[position] = remark
        notifyItemChanged(position)
        adapterListener?.onRefreshData()
    }

    fun replaceAll(newMembers: List<String>) {
        members.clear()
        members.addAll(newMembers)
        notifyDataSetChanged()
        adapterListener?.onRefreshData()
    }

    fun getMembers(): List<String> = members.toList()

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition == toPosition) return true
        Collections.swap(members, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        val minPos = minOf(fromPosition, toPosition)
        val maxPos = maxOf(fromPosition, toPosition)
        notifyItemRangeChanged(minPos, maxPos - minPos + 1)
        return true
    }

    override fun onItemMoveCompleted() {
        adapterListener?.onRefreshData()
    }

    override fun onItemDismiss(position: Int) {
    }

    class MemberViewHolder(val binding: ItemRecyclerProxyChainMemberBinding) :
    BaseViewHolder(binding.root), ItemTouchHelperViewHolder

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(Color.TRANSPARENT)
        }
    }
}
