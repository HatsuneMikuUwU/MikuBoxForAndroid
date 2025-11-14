package com.neko.shapeimageview

import android.content.Context
import android.util.AttributeSet
import com.neko.shapeimageview.shader.ShaderHelper
import com.neko.shapeimageview.shader.SvgShader
import com.neko.shapeimageview.ShaderImageView
import io.nekohasekai.sagernet.R

class CookieImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ShaderImageView(context, attrs, defStyle) {

    override fun createImageViewHelper(): ShaderHelper {
        return SvgShader(R.raw.uwu_shape_cookie)
    }
}
