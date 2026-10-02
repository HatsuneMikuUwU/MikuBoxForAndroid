package com.miku.ray.ui.perappproxy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.databinding.ItemRecyclerBypassListBinding
import com.miku.ray.dto.AppInfo

class PerAppProxyAdapter(
    val apps: List<AppInfo>,
    val viewModel: PerAppProxyViewModel
) : RecyclerView.Adapter<PerAppProxyAdapter.BaseViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is AppViewHolder) {
            val appInfo = apps[position - 1]
            holder.bind(appInfo)
        }
    }

    override fun getItemCount() = apps.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val ctx = parent.context

        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = View(ctx)
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
                )
                BaseViewHolder(view)
            }

            else -> AppViewHolder(ItemRecyclerBypassListBinding.inflate(LayoutInflater.from(ctx), parent, false))
        }
    }

    override fun getItemViewType(position: Int) = if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class AppViewHolder(private val itemBypassBinding: ItemRecyclerBypassListBinding) : BaseViewHolder(itemBypassBinding.root),
    View.OnClickListener {
        private lateinit var appInfo: AppInfo

        fun bind(appInfo: AppInfo) {
            this.appInfo = appInfo

            itemBypassBinding.icon.setImageDrawable(appInfo.appIcon)
            // A system app is marked with a localized suffix instead of the
            // bare "** " prefix, which read like a leaked placeholder and had
            // no legend anywhere on the screen.
            itemBypassBinding.name.text = if (appInfo.isSystemApp) {
                itemBypassBinding.name.context.getString(
                    com.miku.ray.R.string.per_app_system_app_name,
                    appInfo.appName,
                )
            } else {
                appInfo.appName
            }

            itemBypassBinding.packageName.text = appInfo.packageName

            itemBypassBinding.switchButton.isChecked = viewModel.contains(appInfo.packageName)
            // N17: the row's switch is not separately focusable (the row click
            // toggles it), so label it with the app name — an unlabeled switch
            // used to read as a bare "on/off" with no subject.
            itemBypassBinding.switchButton.contentDescription = appInfo.appName

            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            val packageName = appInfo.packageName
            viewModel.toggle(packageName)

            itemBypassBinding.switchButton.isChecked = viewModel.contains(packageName)
        }
    }
}
