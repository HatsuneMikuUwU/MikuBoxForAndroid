package top.uwu.mikubox.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import top.uwu.mikubox.R

class AppListAdapter(
    private val pm: PackageManager,
    private val onToggle: (String, Boolean) -> Unit,
) : RecyclerView.Adapter<AppListAdapter.VH>() {

    data class AppItem(val packageName: String, val label: String, val info: ApplicationInfo)

    private var apps: List<AppItem> = emptyList()
    private val checked = mutableSetOf<String>()
    private var enabled = true

    @SuppressWarnings("NotifyDataSetChanged")
    fun submit(list: List<AppItem>, selected: Set<String>, listEnabled: Boolean) {
        apps = list
        checked.clear()
        checked.addAll(selected)
        enabled = listEnabled
        notifyDataSetChanged()
    }

    @SuppressWarnings("NotifyDataSetChanged")
    fun setEnabled(listEnabled: Boolean) {
        enabled = listEnabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(apps[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.app_icon)
        private val name: TextView = itemView.findViewById(R.id.app_name)
        private val pkg: TextView = itemView.findViewById(R.id.app_package)
        private val check: MaterialCheckBox = itemView.findViewById(R.id.app_check)

        fun bind(item: AppItem) {
            icon.setImageDrawable(item.info.loadIcon(pm))
            name.text = item.label
            pkg.text = item.packageName
            check.isChecked = item.packageName in checked
            itemView.isEnabled = enabled
            itemView.alpha = if (enabled) 1f else 0.4f
            itemView.setOnClickListener {
                if (!enabled) return@setOnClickListener
                val now = item.packageName !in checked
                if (now) checked.add(item.packageName) else checked.remove(item.packageName)
                check.isChecked = now
                onToggle(item.packageName, now)
            }
        }
    }
}
