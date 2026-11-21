package com.neko.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.preference.PreferenceDataStore
import com.neko.shapeimageview.ShaderImageView
import com.neko.shapeimageview.shader.ShaderHelper
import com.neko.shapeimageview.shader.SvgShader
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.ktx.getColorAttr

class DynamicShapeImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ShaderImageView(context, attrs, defStyleAttr), OnPreferenceDataStoreChangeListener {

    private val KEY_SHAPE = "preference_icon_shape"
    
    private var currentShapeId: Int = R.raw.uwu_shape_cookie_9

    override fun createImageViewHelper(): ShaderHelper {
        val shapeId = resolveShapeId()
        currentShapeId = shapeId
        return SvgShader(shapeId)
    }

    init {
        scaleType = ScaleType.CENTER_CROP
        loadColorBitmap()
    }

    private fun loadColorBitmap() {
        try {
            val color = context.getColorAttr(R.attr.colorPrimary)
            
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(color)
            
            setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            DataStore.configurationStore.registerChangeListener(this)
            checkAndUpdateShape()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            DataStore.configurationStore.unregisterChangeListener(this)
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (key == KEY_SHAPE) {
            post { checkAndUpdateShape() }
        }
    }

    private fun resolveShapeId(): Int {
        return try {
            MikuShapeResolver.resolve(DataStore.preferenceIconShape)
        } catch (e: Exception) {
            R.raw.uwu_shape_cookie_9
        }
    }

    private fun checkAndUpdateShape() {
        try {
            val newShapeId = resolveShapeId()
            if (currentShapeId != newShapeId) {
                currentShapeId = newShapeId
                reloadShape()
                invalidate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
