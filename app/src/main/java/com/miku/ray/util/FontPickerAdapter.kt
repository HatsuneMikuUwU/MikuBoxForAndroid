package com.miku.ray.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.R

class FontPickerAdapter(
    private val context: Context,
    private val values: Array<String>,
    private val labels: Array<String>,
    private var selectedValue: String,
    private val onSelect: (value: String, label: String) -> Unit
) : RecyclerView.Adapter<FontPickerAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.textFontLabel)
        val check: ImageView = view.findViewById(R.id.imageFontCheck)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_font_picker, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = values.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val value = values[position]
        val label = labels.getOrElse(position) { value }

        holder.label.text = label
        holder.label.typeface = AppFontResolver.getTypeface(context, value)

        val isSelected = value == selectedValue
        holder.check.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (value != selectedValue) {
                val previous = selectedValue
                selectedValue = value
                notifyItemChanged(values.indexOf(previous))
                notifyItemChanged(position)
            }
            onSelect(value, label)
        }
    }
}
