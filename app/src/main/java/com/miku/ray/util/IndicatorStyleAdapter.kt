package com.miku.ray.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.card.MaterialCardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.R

class IndicatorStyleAdapter(
    private val context: Context,
    private val selected: IndicatorStyle,
    private val onSelect: (IndicatorStyle) -> Unit
) : RecyclerView.Adapter<IndicatorStyleAdapter.ViewHolder>() {

    private val styles = IndicatorStyle.values()

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val card = view.findViewById<MaterialCardView>(R.id.cardImage)
        val container = view.findViewById<LinearLayout>(R.id.imagePreviewContainer)
        val contentContainer = view.findViewById<LinearLayout>(R.id.contentContainer)
        val check = view.findViewById<ImageView>(R.id.imageCheck)
        val overlay = view.findViewById<LinearLayout>(R.id.overlayContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_indicator_style, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = styles.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val style = styles[position]

        holder.container.background = ContextCompat.getDrawable(context, style.drawableRes)

        val isSelected = style == selected

        if (isSelected) {
            holder.check.visibility = View.VISIBLE
            holder.contentContainer.visibility = View.INVISIBLE
        } else {
            holder.check.visibility = View.GONE
            holder.contentContainer.visibility = View.VISIBLE
        }

        holder.view.setOnClickListener {
            onSelect(style)
        }
    }
}
