package com.miku.ray.marquee.text

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager

class UserNameTextView : AppCompatTextView {

    private var mAggregatedVisible: Boolean = false

    constructor(context: Context) : super(context) { init() }
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { init() }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { init() }

    private fun init() {
        mAggregatedVisible = false
        updateTextFromStore()
    }

    private fun updateTextFromStore() {
        var rawName = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_PROFILE_NAME)

        if (rawName.isNullOrEmpty()) {
            rawName = context.getString(R.string.uwu_profile_banner_title)
        }

        val finalString = context.getString(R.string.uwu_profile_banner_title_custom, rawName)

        if (text.toString() != finalString) {
            text = finalString
            isSelected = false
            isSelected = true
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isSelected = true
        updateTextFromStore()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            updateTextFromStore()
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
        if (isVisible == mAggregatedVisible) return
        mAggregatedVisible = isVisible
        ellipsize = if (mAggregatedVisible) TextUtils.TruncateAt.MARQUEE else TextUtils.TruncateAt.END
    }

    private fun View.isVisibleToUser(): Boolean = this.visibility == View.VISIBLE
}
