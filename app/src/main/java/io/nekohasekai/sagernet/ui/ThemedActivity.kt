package io.nekohasekai.sagernet.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.MenuItem
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.utils.DPIController
import io.nekohasekai.sagernet.utils.Theme

abstract class ThemedActivity : AppCompatActivity {
    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    override fun attachBaseContext(newBase: Context) {
        val dpi = DataStore.dpiValue
        val baseScale = DataStore.fontSize.toFloat() / 100f
        val fontScale = if (DataStore.boldFontEnabled) baseScale + 0.05f else baseScale
        val context = if (dpi > 0) DPIController.wrapWithDpi(newBase, dpi) else newBase
        val res = context.resources
        val configuration = Configuration(res.configuration)
        val metrics = res.displayMetrics
        configuration.fontScale = fontScale
        
        if (dpi > 0) {
            val targetDensity = dpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT
            configuration.densityDpi = dpi
            metrics.density = targetDensity
            metrics.scaledDensity = targetDensity * fontScale
        } else {
            metrics.scaledDensity = metrics.density * fontScale
        }
        val finalContext = context.createConfigurationContext(configuration)
        finalContext.resources.displayMetrics.setTo(metrics)
        super.attachBaseContext(finalContext)
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    var themeResId = 0
    var uiMode = 0
    open val isDialog = false

    override fun onCreate(savedInstanceState: Bundle?) {
        
        if (!isDialog) {
            Theme.apply(this)
        } else {
            Theme.applyDialog(this)
            Theme.applyWindowBlur(window)
        }
        
        if (DataStore.boldFontEnabled) {
            theme.applyStyle(R.style.BoldTextThemeOverlay, true)
        }

        val fontOverlayId = getFontResId(DataStore.appFont)
        if (fontOverlayId != 0) {
            theme.applyStyle(fontOverlayId, true)
        }
        
        Theme.applyNightTheme()

        super.onCreate(savedInstanceState)
        
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                if (f is DialogFragment) {
                    Theme.applyWindowBlur(f.dialog?.window)
                }
            }
        }, true)

        uiMode = resources.configuration.uiMode

        val dpi = DataStore.dpiValue
        if (dpi > 0) {
            DPIController.applyDpi(this, dpi)
        }

        if (Build.VERSION.SDK_INT >= 35) {
             findViewById<android.view.View>(android.R.id.content)?.let { rootView ->
                ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
                    val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                    findViewById<AppBarLayout>(R.id.appbar)?.apply {
                        updatePadding(top = top)
                    }
                    insets
                }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        
        if (DataStore.boldFontEnabled) {
            findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar)?.apply {
                val customTypeface = getCustomTypeface(this@ThemedActivity, DataStore.appFont)
                val boldTypeface = Typeface.create(customTypeface, Typeface.BOLD)
                setCollapsedTitleTypeface(boldTypeface)
                setExpandedTitleTypeface(boldTypeface)
            }
        }
    }

    override fun setTheme(resId: Int) {
        super.setTheme(resId)
        themeResId = resId
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.uiMode != uiMode) {
            uiMode = newConfig.uiMode
            ActivityCompat.recreate(this)
        }
    }
    
    override fun startActivity(intent: Intent?) {
        super.startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        super.startActivity(intent, options)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    fun snackbar(@StringRes resId: Int): Snackbar = snackbar("").setText(resId)
    fun snackbar(text: CharSequence): Snackbar = snackbarInternal(text).apply {
        view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).apply {
            maxLines = 10
        }
    }

    internal open fun snackbarInternal(text: CharSequence): Snackbar = throw NotImplementedError()

    private fun getFontResId(fontName: String?): Int = 0

    fun getCustomTypeface(context: Context, fontName: String?): Typeface = Typeface.DEFAULT
}