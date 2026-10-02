package com.miku.ray.ui.weather

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.miku.ray.R
import com.miku.ray.util.getColorAttr

class WeatherHourlyAdapter(
    private val context: Context,
    private val items: List<HourlyForecastItem>
) : RecyclerView.Adapter<WeatherHourlyAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardTemp: MaterialCardView = view.findViewById(R.id.cardHourlyTemp)
        val tvTemp: TextView = view.findViewById(R.id.tvHourlyTemp)
        val tvPrecip: TextView = view.findViewById(R.id.tvHourlyPrecip)
        val ivIcon: ImageView = view.findViewById(R.id.ivHourlyIcon)
        val tvTime: TextView = view.findViewById(R.id.tvHourlyTime)
        val tvDay: TextView = view.findViewById(R.id.tvHourlyDay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_weather_hourly, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTemp.text = "${item.tempCelsius}\u00b0"
        holder.ivIcon.setImageResource(item.iconRes)
        holder.tvTime.text = item.timeLabel
        holder.tvDay.text = item.dayLabel

        if (item.precipProbability > 0) {
            holder.tvPrecip.visibility = View.VISIBLE
            holder.tvPrecip.text = "${item.precipProbability}%"
        } else {
            holder.tvPrecip.visibility = View.INVISIBLE
        }

        if (item.isNow) {
            holder.cardTemp.setCardBackgroundColor(context.getColorAttr("colorPrimary"))
            holder.tvTemp.setTextColor(context.getColorAttr("colorOnPrimary"))
        } else {
            holder.cardTemp.setCardBackgroundColor(context.getColorAttr("colorSurfaceContainerHighest"))
            holder.tvTemp.setTextColor(context.getColorAttr("colorOnSurface"))
        }
    }
}
