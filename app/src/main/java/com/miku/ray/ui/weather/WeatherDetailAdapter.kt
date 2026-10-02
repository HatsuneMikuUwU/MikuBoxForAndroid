package com.miku.ray.ui.weather

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.R

class WeatherDetailAdapter(
    private val context: Context,
    private val items: List<DetailItem>
) : RecyclerView.Adapter<WeatherDetailAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivDetailIcon)
        val tvValue: TextView = view.findViewById(R.id.tvDetailValue)
        val tvLabel: TextView = view.findViewById(R.id.tvDetailLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_weather_detail, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.ivIcon.setImageResource(item.iconRes)
        holder.tvValue.text = item.value
        holder.tvLabel.text = item.label
    }
}
