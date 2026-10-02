package com.miku.ray.ui.logcat

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.AppConfig
import com.miku.ray.databinding.ItemRecyclerLogcatBinding
import com.miku.ray.util.LogEntry
import com.miku.ray.util.LogUtil

class LogcatRecyclerAdapter(
    private val viewModel: LogcatViewModel,
    private val onLongClick: ((String) -> Boolean)? = null
) : RecyclerView.Adapter<LogcatRecyclerAdapter.MainViewHolder>() {

    override fun getItemCount() = viewModel.getAll().size

    override fun onBindViewHolder(holder: MainViewHolder, position: Int) {
        try {
            val logs = viewModel.getAll()
            val raw = logs[position]

            if (raw.isEmpty()) {
                holder.itemSubSettingBinding.logTag.text = ""
                holder.itemSubSettingBinding.logContent.text = ""
            } else {
                val entry = LogEntry.parse(raw)
                val tagLabel = if (entry.tag.isNotEmpty()) {
                    "${levelLabel(entry.level)} ${entry.tag}  ${entry.timestamp}".trim()
                } else {
                    entry.timestamp
                }
                holder.itemSubSettingBinding.logTag.text = tagLabel
                holder.itemSubSettingBinding.logTag.setTextColor(colorForLevel(entry.level))
                holder.itemSubSettingBinding.logContent.text = entry.message.ifEmpty { entry.raw }
            }

            holder.itemView.setOnLongClickListener {
                onLongClick?.invoke(raw) ?: false
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error binding log view data", e)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainViewHolder {
        return MainViewHolder(
            ItemRecyclerLogcatBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    class MainViewHolder(val itemSubSettingBinding: ItemRecyclerLogcatBinding) : RecyclerView.ViewHolder(itemSubSettingBinding.root)

    companion object {
        private fun levelLabel(level: Char): String = when (level) {
            'V' -> "(verbose)"
            'D' -> "(debug)"
            'I' -> "(info)"
            'W' -> "(warn)"
            'E' -> "(error)"
            'F' -> "(fatal)"
            else -> "(log)"
        }

        private fun colorForLevel(level: Char): Int = when (level) {
            'E', 'F' -> Color.parseColor("#F44336")
            'W' -> Color.parseColor("#FFA000")
            'I' -> Color.parseColor("#4CAF50")
            'D' -> Color.parseColor("#29B6F6")
            'V' -> Color.parseColor("#9E9E9E")
            else -> Color.parseColor("#9E9E9E")
        }
    }
}
