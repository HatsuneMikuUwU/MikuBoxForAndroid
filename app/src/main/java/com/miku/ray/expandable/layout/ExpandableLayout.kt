package com.miku.ray.expandable.layout

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.card.MaterialCardView
import com.miku.ray.R

class ExpandableLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs), View.OnClickListener {

    private var arrowIcon: ImageView? = null
    private var cardExpandable: MaterialCardView? = null
    private var expandableContent: ExpandableView? = null

    private val headerViewId: Int
    private val arrowViewId: Int
    private val contentViewId: Int

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ExpandableLayout)
        headerViewId = a.getResourceId(R.styleable.ExpandableLayout_el_headerViewId, View.NO_ID)
        arrowViewId = a.getResourceId(R.styleable.ExpandableLayout_el_arrowViewId, View.NO_ID)
        contentViewId = a.getResourceId(R.styleable.ExpandableLayout_el_contentViewId, View.NO_ID)
        a.recycle()

        require(headerViewId != View.NO_ID && arrowViewId != View.NO_ID && contentViewId != View.NO_ID) {
            "ExpandableLayout requires app:el_headerViewId, app:el_arrowViewId and app:el_contentViewId to be set"
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        expandableContent = findViewById(contentViewId)
        arrowIcon = findViewById(arrowViewId)
        cardExpandable = findViewById(headerViewId)

        cardExpandable?.setOnClickListener(this)
        arrowIcon?.setOnClickListener(this)

        initializeLogic()
    }

    override fun onClick(view: View) {
        setOnclick(view)
    }

    private fun setOnclick(view: View) {
        expandableContent?.let { content ->
            if (content.isExpanded) {
                content.collapse()
                arrowIcon?.animate()?.setDuration(300L)?.rotation(0.0f)
            } else {
                content.expand()
                arrowIcon?.animate()?.setDuration(300L)?.rotation(90.0f)
            }
        }
    }

    private fun initializeLogic() {
        arrowIcon?.apply {
            background = RippleDrawable(
                ColorStateList(arrayOf(intArrayOf()), intArrayOf(-0x8a8a8b)),
                null,
                null
            )
            isClickable = true
            // N19: the header card carries the text and the click, so the
            // arrow is a decorative duplicate button to a screen reader —
            // exclude it instead of announcing an unlabeled toggle.
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO

            rotation = if (expandableContent?.isExpanded == true) 90.0f else 0.0f
        }
    }
}
