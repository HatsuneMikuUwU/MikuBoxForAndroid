package top.uwu.mikubox.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import top.uwu.mikubox.R
import top.uwu.mikubox.profile.MihomoProfileStore

class ProfileAdapter(
    private val onSelect: (MihomoProfileStore.Profile) -> Unit,
    private val onUpdate: (MihomoProfileStore.Profile) -> Unit,
    private val onDelete: (MihomoProfileStore.Profile) -> Unit,
) : RecyclerView.Adapter<ProfileAdapter.VH>() {

    private var profiles: List<MihomoProfileStore.Profile> = emptyList()
    private var selectedId: String? = null

    @SuppressWarnings("NotifyDataSetChanged")
    fun submit(list: List<MihomoProfileStore.Profile>, selectedId: String?) {
        this.profiles = list
        this.selectedId = selectedId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false))

    override fun getItemCount(): Int = profiles.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val profile = profiles[position]
        holder.bind(profile, profile.id == selectedId)
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView as MaterialCardView
        private val name: TextView = itemView.findViewById(R.id.tv_name)
        private val meta: TextView = itemView.findViewById(R.id.tv_meta)
        private val update: MaterialButton = itemView.findViewById(R.id.btn_update)
        private val delete: MaterialButton = itemView.findViewById(R.id.btn_delete)

        fun bind(profile: MihomoProfileStore.Profile, selected: Boolean) {
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
            card.isChecked = selected
            update.visibility = if (profile.isSubscription) View.VISIBLE else View.GONE
            card.setOnClickListener { onSelect(profile) }
            update.setOnClickListener { onUpdate(profile) }
            delete.setOnClickListener { onDelete(profile) }
        }
    }
}
