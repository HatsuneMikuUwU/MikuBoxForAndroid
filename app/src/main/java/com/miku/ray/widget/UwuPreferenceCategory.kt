package com.miku.ray.widget

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.widget.ImageView
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import com.miku.ray.util.getColorAttr
import com.miku.ray.R

class UwuPreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : PreferenceCategory(context, attrs) {

    private var sectionIconRes: Int = 0

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.UwuHeaderIconView)
        sectionIconRes = ta.getResourceId(R.styleable.UwuHeaderIconView_sectionIcon, 0)
        ta.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val iconView = holder.itemView.findViewById<ImageView>(R.id.uwu_category_icon)
        ?: return
        if (sectionIconRes != 0) {
            iconView.setImageResource(sectionIconRes)
        }
        val frame = iconView.parent as? android.view.ViewGroup ?: return

        val colorStart = context.getColorAttr("colorPrimary")
        val colorEnd = context.getColorAttr("colorTertiary")

        frame.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(colorStart, colorEnd)
        ).apply { shape = GradientDrawable.OVAL }
    }
}
