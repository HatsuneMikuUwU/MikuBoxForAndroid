package com.miku.ray.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.miku.ray.AppConfig
import com.miku.ray.R

class AppIconPickerAdapter(
    private val context: Context,
    private val selectedValue: String,
    private val onSelect: (String) -> Unit,
) : RecyclerView.Adapter<AppIconPickerAdapter.VH>() {

    companion object {
        private val MIPMAP_NAMES: Map<String, String> = mapOf(
            AppConfig.APP_ICON_DEFAULT to "ic_launcher",
            AppConfig.APP_ICON_MIKU_TEAL to "ic_launcher_miku_teal",
            AppConfig.APP_ICON_BASIC to "ic_launcher_basic",
            AppConfig.APP_ICON_CHERRY_POP to "ic_launcher_cherry_pop",
            AppConfig.APP_ICON_RABBIT_HOLE to "ic_launcher_rabbit_hole",
            AppConfig.APP_ICON_MESMERIZER to "ic_launcher_mesmerizer",
            AppConfig.APP_ICON_SAKURA to "ic_launcher_sakura",
            AppConfig.APP_ICON_MAGICAL_MIRAI_2024 to "ic_launcher_magical_mirai_2024",
            AppConfig.APP_ICON_DEEP_SEA_GIRL to "ic_launcher_deep_sea_girl",
            AppConfig.APP_ICON_SNOW_MIKU_2025 to "ic_launcher_snow_miku_2025",
            AppConfig.APP_ICON_SYMPHONY_2022 to "ic_launcher_symphony_2022",
            AppConfig.APP_ICON_RACING_MIKU_2025 to "ic_launcher_racing_miku_2025",
            AppConfig.APP_ICON_CINNAMIKU to "ic_launcher_cinnamiku",
            AppConfig.APP_ICON_RETRY_NOW to "ic_launcher_retry_now",
        )

        fun icons(context: Context): List<Triple<String, String, String>> {
            val values = context.resources.getStringArray(R.array.app_icon_values)
            val entries = context.resources.getStringArray(R.array.app_icon_entries)
            return values.mapIndexed { i, value ->
                val label = entries.getOrElse(i) { value }
                val mipmapName = MIPMAP_NAMES[value] ?: "ic_launcher"
                Triple(value, mipmapName, label)
            }
        }
    }

    private var selected: String = selectedValue
    private val items: List<Triple<String, String, String>> = icons(context)

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card_container)
        val icon: ImageView = view.findViewById(R.id.icon_image)
        val label: TextView = view.findViewById(R.id.icon_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(context)
        .inflate(R.layout.item_app_icon_picker, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (value, mipmapName, label) = items[position]
        val resId = context.resources.getIdentifier(mipmapName, "mipmap", context.packageName)
        val isSelected = value == selected

        if (resId != 0) {
            holder.icon.setImageResource(resId)
        } else {
            holder.icon.setImageDrawable(null)
        }

        holder.label.text = label
        holder.card.strokeColor = if (isSelected) {
            context.getColorAttr("colorPrimary")
        } else {
            android.graphics.Color.TRANSPARENT
        }

        holder.itemView.setOnClickListener {
            val prevIdx = items.indexOfFirst { it.first == selected }
            selected = value
            onSelect(value)
            if (prevIdx >= 0) notifyItemChanged(prevIdx)
            notifyItemChanged(position)
        }
    }
}
