package com.miku.ray.ui.bottomsheet

import android.net.Uri
import android.view.View
import androidx.core.view.ViewCompat
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.miku.ray.util.WindowBlurUtils
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.particlesdrawable.ParticlesView
import com.miku.ray.util.ParticlesController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import android.widget.ImageView
import com.miku.ray.util.getColorAttr

abstract class BaseBottomSheetFragment : BottomSheetDialogFragment() {

    protected fun setupParticles(view: View) {
        val particlesView = view.findViewById<ParticlesView>(R.id.ParticlesView) ?: return
        val enabled = MmkvManager.decodeSettingsBool(AppConfig.PREF_ENABLE_PARTICLES_SHEET, false)
        particlesView.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            ParticlesController.applyTo(particlesView)
        }
    }

    private fun computeDimColor(context: android.content.Context): Int {
        val dimPercent = MmkvManager.decodeSettingsInt(
            AppConfig.PREF_SHEET_BANNER_DIM,
            AppConfig.SHEET_BANNER_DIM_DEFAULT
        ).coerceIn(AppConfig.SHEET_BANNER_DIM_MIN, AppConfig.SHEET_BANNER_DIM_MAX)

        val alpha = (dimPercent * 255 / 100).coerceIn(0, 255)
        val baseColor = context.getColorAttr("colorCard")

        return android.graphics.Color.argb(
            alpha,
            android.graphics.Color.red(baseColor),
            android.graphics.Color.green(baseColor),
            android.graphics.Color.blue(baseColor)
        )
    }

    protected fun loadBannerSheet(view: View) {
        val bannerImageView = view.findViewById<ImageView>(R.id.img_banner_sheet) ?: return
        bannerImageView.setLayerType(View.LAYER_TYPE_NONE, null)

        val dimColor = computeDimColor(bannerImageView.context)

        view.findViewById<View>(R.id.view_banner_sheet_dim)?.let { dimView ->
            dimView.setBackgroundColor(dimColor)
        }
        val uriString = MmkvManager.decodeSettingsString(AppConfig.PREF_CUSTOM_SHEET_BANNER_URI)
        val targetTag = if (uriString.isNullOrBlank()) TAG_SHEET_DEFAULT else uriString
        if (bannerImageView.tag != targetTag) {
            if (!uriString.isNullOrBlank()) {
                val isGif = uriString.lowercase().endsWith(".gif")
                if (isGif) {
                    Glide.with(this)
                    .asGif()
                    .load(Uri.parse(uriString))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .error(R.drawable.uwu_banner_sheet)
                    .into(bannerImageView)
                } else {
                    Glide.with(this)
                    .load(Uri.parse(uriString))
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .error(R.drawable.uwu_banner_sheet)
                    .into(bannerImageView)
                }
            } else {
                Glide.with(this).clear(bannerImageView)
                bannerImageView.setImageResource(R.drawable.uwu_banner_sheet)
            }
            bannerImageView.tag = targetTag
        }
    }

    override fun onDestroyView() {
        view?.findViewById<ImageView>(R.id.img_banner_sheet)?.let { bannerImageView ->
            val context = bannerImageView.context.applicationContext
            Glide.with(context).clear(bannerImageView)
            bannerImageView.setImageDrawable(null)
            bannerImageView.tag = null
        }
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? BottomSheetDialog ?: return

        sheetDialog.window?.let { window ->
            WindowBlurUtils.applyWindowBlur(window)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }

        val bottomSheet = sheetDialog.findViewById<android.view.View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        bottomSheet.clipToOutline = true

        sheetDialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            val screenHeight = view.resources.displayMetrics.heightPixels
            val baseSizePx = (8 * view.resources.displayMetrics.density).toInt()

            sheetDialog.behavior.maxHeight = screenHeight - statusBarInset - baseSizePx

            view.findViewById<android.view.View>(R.id.bottom_sheet)?.updatePadding(
                bottom = baseSizePx + navBarInset
            )

            insets
        }
    }

    companion object {
        private const val TAG_SHEET_DEFAULT = "DEFAULT_BANNER_SHEET"
    }
}
