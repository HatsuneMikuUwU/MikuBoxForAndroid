package com.miku.ray.ui.userasset

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.R
import com.miku.ray.contracts.BaseAdapterListener
import com.miku.ray.databinding.ItemRecyclerUserAssetBinding
import com.miku.ray.extension.toTrafficString
import java.text.DateFormat
import java.util.Date

class UserAssetAdapter(
    private val viewModel: UserAssetViewModel,
    private val adapterListener: BaseAdapterListener?
) : RecyclerView.Adapter<UserAssetAdapter.UserAssetViewHolder>() {

    override fun getItemCount() = viewModel.uiState.value.assets.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserAssetViewHolder {
        return UserAssetViewHolder(
            ItemRecyclerUserAssetBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: UserAssetViewHolder, position: Int) {
        val state = viewModel.uiState.value
        val item = state.assets.getOrNull(position) ?: return
        val fileMetadata = state.fileMetadata[item.guid]

        with(holder.binding) {
            assetName.text = item.assetUrl.remarks

            if (fileMetadata != null) {
                val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                assetProperties.text =
                "${fileMetadata.length.toTrafficString()}  •  ${dateFormat.format(Date(fileMetadata.lastModified))}"
            } else {
                assetProperties.text = root.context.getString(R.string.msg_file_not_found)
            }

            layoutEdit.isVisible = item.assetUrl.locked != true && item.assetUrl.url != "file"

            layoutEdit.setOnClickListener {
                adapterListener?.onEdit(item.guid, position)
            }

            layoutRemove.setOnClickListener {
                adapterListener?.onRemove(item.guid, position)
            }

            layoutCard.setOnClickListener {
            }
        }
    }

    class UserAssetViewHolder(val binding: ItemRecyclerUserAssetBinding) :
    RecyclerView.ViewHolder(binding.root)
}
