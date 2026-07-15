package top.uwu.mikubox.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import top.uwu.mikubox.R
import top.uwu.mikubox.profile.MihomoProfileStore
import top.uwu.mikubox.profile.MihomoTrafficStore

class ProfileAdapter(
    private val onSelect: (MihomoProfileStore.Profile) -> Unit,
    private val onUpdate: (MihomoProfileStore.Profile) -> Unit,
    private val onDelete: (MihomoProfileStore.Profile) -> Unit,
    private val onShare: (MihomoProfileStore.Profile) -> Unit,
) : RecyclerView.Adapter<ProfileAdapter.VH>() {

    private var profiles: List<MihomoProfileStore.Profile> = emptyList()
    private var selectedId: String? = null
    private var updatingId: String? = null

    @SuppressWarnings("NotifyDataSetChanged")
    fun submit(list: List<MihomoProfileStore.Profile>, selectedId: String?) {
        this.profiles = list
        this.selectedId = selectedId
        notifyDataSetChanged()
    }

    /** Show/hide the indeterminate progress bar on the card being refreshed. */
    @SuppressWarnings("NotifyDataSetChanged")
    fun setUpdating(profileId: String?) {
        this.updatingId = profileId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false))

    override fun getItemCount(): Int = profiles.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val profile = profiles[position]
        holder.bind(profile, profile.id == selectedId, profile.id == updatingId)
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView as MaterialCardView
        private val progress: LinearProgressIndicator = itemView.findViewById(R.id.update_progress)
        private val selectedBar: View = itemView.findViewById(R.id.selected_view)
        private val name: TextView = itemView.findViewById(R.id.tv_name)
        private val meta: TextView = itemView.findViewById(R.id.tv_meta)
        private val traffic: TextView = itemView.findViewById(R.id.tv_traffic)
        private val update: MaterialButton = itemView.findViewById(R.id.btn_update)
        private val overflow: MaterialButton = itemView.findViewById(R.id.btn_overflow)

        fun bind(profile: MihomoProfileStore.Profile, selected: Boolean, updating: Boolean) {
            val ctx = itemView.context
            name.text = profile.name
            val type = ctx.getString(
                if (profile.isSubscription) R.string.badge_subscription else R.string.badge_local
            )
            meta.text = if (selected) {
                ctx.getString(R.string.badge_selected) + " · " + type
            } else {
                type
            }
            val totals = MihomoTrafficStore.totals(ctx, profile.id)
            if (totals.upload > 0 || totals.download > 0) {
                traffic.visibility = View.VISIBLE
                traffic.text = ctx.getString(
                    R.string.profile_traffic,
                    formatBytes(totals.upload),
                    formatBytes(totals.download),
                )
            } else {
                traffic.visibility = View.GONE
            }
            progress.visibility = if (updating) View.VISIBLE else View.GONE
            selectedBar.visibility = View.GONE
            card.strokeWidth = if (selected) dpToPx(2) else dpToPx(1)
            card.strokeColor = MaterialColors.getColor(
                itemView,
                if (selected) {
                    androidx.appcompat.R.attr.colorPrimary
                } else {
                    com.google.android.material.R.attr.colorOutlineVariant
                },
            )
            update.visibility = if (profile.isSubscription) View.VISIBLE else View.GONE
            card.setOnClickListener { onSelect(profile) }
            update.setOnClickListener { onUpdate(profile) }
            overflow.setOnClickListener { showMenu(it, profile) }
        }

        private fun showMenu(anchor: View, profile: MihomoProfileStore.Profile) {
            PopupMenu(anchor.context, anchor).apply {
                menuInflater.inflate(R.menu.menu_profile, menu)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_select -> onSelect(profile)
                        R.id.action_share -> onShare(profile)
                        R.id.action_delete -> onDelete(profile)
                    }
                    true
                }
                show()
            }
        }

        private fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val units = arrayOf("KiB", "MiB", "GiB", "TiB")
            var value = bytes.toDouble() / 1024
            var unit = 0
            while (value >= 1024 && unit < units.lastIndex) {
                value /= 1024
                unit++
            }
            return String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
        }

        private fun dpToPx(dp: Int): Int =
            (dp * itemView.resources.displayMetrics.density).toInt()
    }
}
