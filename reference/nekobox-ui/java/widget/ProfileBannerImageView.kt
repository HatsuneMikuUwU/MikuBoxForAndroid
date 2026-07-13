package io.nekohasekai.sagernet.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.neko.shapeimageview.ShaderImageView
import com.neko.shapeimageview.shader.ShaderHelper
import com.neko.shapeimageview.shader.SvgShader
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore

/**
 * Ported from MikuRay. Shows the user's profile avatar (or a default
 * silhouette), clipped to their chosen SVG shape, loaded through Glide so a
 * custom picture picked from the gallery is cached and reused.
 */
class ProfileBannerImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ShaderImageView(context, attrs, defStyleAttr) {

    private var currentShapeKey: String = DynamicShapeImageView.DEFAULT_SHAPE

    private val shapeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Action.PROFILE_BANNER_CHANGED) {
                post {
                    checkAndUpdateShape()
                    loadImage()
                }
            }
        }
    }

    override fun createImageViewHelper(): ShaderHelper {
        currentShapeKey = resolveShapeKey()
        return SvgShader(DynamicShapeImageView.shapeIdFor(currentShapeKey))
    }

    init {
        scaleType = ScaleType.CENTER_CROP
        setLayerType(View.LAYER_TYPE_NONE, null)
        elevation = 0f
        outlineProvider = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            val filter = IntentFilter(Action.PROFILE_BANNER_CHANGED)
            ContextCompat.registerReceiver(
                context, shapeChangeReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            checkAndUpdateShape()
            loadImage()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!isInEditMode) {
            try {
                context.unregisterReceiver(shapeChangeReceiver)
            } catch (_: Exception) {
            }
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && !isInEditMode) {
            checkAndUpdateShape()
            loadImage()
        }
    }

    private fun resolveShapeKey(): String =
        DataStore.profileBannerShape.ifEmpty { DynamicShapeImageView.DEFAULT_SHAPE }

    private fun checkAndUpdateShape() {
        val newKey = resolveShapeKey()
        if (currentShapeKey != newKey) {
            currentShapeKey = newKey
            reloadShape()
            invalidate()
        }
    }

    private fun loadImage() {
        try {
            val uriString = DataStore.profileBannerUri
            val targetTag = uriString.ifEmpty { TAG_PROFILE_DEFAULT }

            if (this.tag != targetTag) {
                if (uriString.isNotEmpty()) {
                    val savedUri = Uri.parse(uriString)
                    Glide.with(this)
                        .asBitmap()
                        .load(savedUri)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .dontAnimate()
                        .error(R.drawable.uwu_banner_profile)
                        .into(this)
                } else {
                    loadDefault()
                }
                this.tag = targetTag
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (this.tag != TAG_PROFILE_DEFAULT) {
                loadDefault()
                this.tag = TAG_PROFILE_DEFAULT
            }
        }
    }

    private fun loadDefault() {
        Glide.with(this).clear(this)
        setImageResource(R.drawable.uwu_banner_profile)
    }

    companion object {
        private const val TAG_PROFILE_DEFAULT = "DEFAULT_BANNER_PROFILE"
    }
}
