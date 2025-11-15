package moe.matsuri.nb4a.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceViewHolder
import io.nekohasekai.sagernet.R

class CardEditPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : EditTextPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        layoutResource = R.layout.uwu_banner_profile
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        holder.itemView.isClickable = false
        
        val cardView: View? = holder.findViewById(R.id.on_click)

        cardView?.setOnClickListener {
            preferenceManager?.showDialog(this) 
        }

        val summaryTextView = holder.findViewById(android.R.id.summary) as? TextView
        summaryTextView?.text = text 
    }
}
