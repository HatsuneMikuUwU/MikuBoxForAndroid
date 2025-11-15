package moe.matsuri.nb4a.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R

class CardEditPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : EditTextPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        widgetLayoutResource = R.layout.uwu_banner_profile
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        holder.itemView.isClickable = false

        val cardClick = holder.findViewById(R.id.on_click)

        cardClick?.setOnClickListener {
            performClick()
        }

        val summaryText = holder.findViewById(android.R.id.summary) as? TextView
        summaryText?.text = text
    }
}