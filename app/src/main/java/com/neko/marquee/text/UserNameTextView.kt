package com.neko.marquee.text

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore

class UserNameTextView : AppCompatTextView {
    
    private var mAggregatedVisible: Boolean = false

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        mAggregatedVisible = false
        updateTextFromDataStore()
    }

    private fun updateTextFromDataStore() {
        var rawName = DataStore.customProfileName

        if (rawName.isNullOrEmpty()) {
            rawName = context.getString(R.string.uwu_banner_title)
        }

        val finalString = context.getString(R.string.uwu_banner_title_custom, rawName)

        if (text.toString() != finalString) {
            text = finalString
            
            isSelected = false
            isSelected = true
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isSelected = true
        updateTextFromDataStore()
    }
    
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            updateTextFromDataStore()
            isSelected = true 
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isSelected = false
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        onVisibilityAggregated(isVisibleToUser())
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible == mAggregatedVisible) {
            return
        }
        mAggregatedVisible = isVisible
        if (mAggregatedVisible) {
            ellipsize = TextUtils.TruncateAt.MARQUEE
        } else {
            ellipsize = TextUtils.TruncateAt.END
        }
    }
    
    fun View.isVisibleToUser(): Boolean {
        return this.visibility == View.VISIBLE
    }
}
