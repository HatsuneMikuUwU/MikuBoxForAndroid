package com.miku.ray.ui.weather

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.R

class WeatherDailyAdapter(
    private val context: Context,
    private val items: List<DailyForecastItem>
) : RecyclerView.Adapter<WeatherDailyAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMax: TextView = view.findViewById(R.id.tvDailyMax)
        val tvMin: TextView = view.findViewById(R.id.tvDailyMin)
        val ivIcon: ImageView = view.findViewById(R.id.ivDailyIcon)
        val tvPrecip: TextView = view.findViewById(R.id.tvDailyPrecip)
        val tvWeekday: TextView = view.findViewById(R.id.tvDailyWeekday)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_weather_daily, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvMax.text = "${item.maxTempCelsius}\u00b0"
        holder.tvMin.text = "${item.minTempCelsius}\u00b0"
        holder.ivIcon.setImageResource(item.iconRes)
        holder.tvWeekday.text = item.weekdayLabel

        if (item.precipProbability > 0) {
            holder.tvPrecip.visibility = View.VISIBLE
            holder.tvPrecip.text = "${item.precipProbability}%"
        } else {
            holder.tvPrecip.visibility = View.INVISIBLE
        }
    }
}
