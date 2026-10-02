package com.miku.ray.toasty

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.miku.ray.R

@Suppress("DEPRECATION")
internal object ToastyUtils {

    @JvmStatic
    fun tintIcon(drawable: Drawable, @ColorInt tintColor: Int): Drawable {
        drawable.mutate().setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        return drawable
    }

    @JvmStatic
    fun tint9PatchDrawableFrame(context: Context, @ColorInt tintColor: Int): Drawable? {
        val toastDrawable = getDrawable(context, R.drawable.uwu_bg_sin)
        return if (toastDrawable != null) {
            tintIcon(toastDrawable, tintColor)
        } else null
    }

    @JvmStatic
    fun setBackground(view: View, drawable: Drawable?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.background = drawable
        } else {
            view.setBackgroundDrawable(drawable)
        }
    }

    @JvmStatic
    fun getDrawable(context: Context, @DrawableRes id: Int): Drawable? {
        return AppCompatResources.getDrawable(context, id)
    }

    @JvmStatic
    fun getColor(context: Context, @ColorRes color: Int): Int {
        return ContextCompat.getColor(context, color)
    }

    @JvmStatic
    fun getColorAttr(context: Context, attrName: String, @ColorInt fallbackColor: Int): Int {
        val attrId = context.resources.getIdentifier(attrName, "attr", context.packageName)
        val typedValue = TypedValue()
        return if (attrId != 0 && context.theme.resolveAttribute(attrId, typedValue, true)) {
            typedValue.data
        } else {
            fallbackColor
        }
    }
}
