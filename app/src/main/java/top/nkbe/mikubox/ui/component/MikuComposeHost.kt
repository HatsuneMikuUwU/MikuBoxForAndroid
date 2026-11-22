package top.nkbe.mikubox.ui.component

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import top.nkbe.mikubox.ui.theme.MikuBoxTheme

/**
 * Creates a lifecycle-aware ComposeView wrapped in [MikuBoxTheme] for hybrid Fragment/Compose migration.
 */
fun Fragment.mikuComposeView(
    content: @Composable () -> Unit,
): ComposeView = requireContext().mikuComposeView(content)

fun Context.mikuComposeView(
    content: @Composable () -> Unit,
): ComposeView = ComposeView(this).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        MikuBoxTheme(content = content)
    }
}

fun ComponentActivity.setMikuContent(
    content: @Composable () -> Unit,
) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    @Suppress("DEPRECATION")
    window.statusBarColor = Color.TRANSPARENT
    setContentView(mikuComposeView(content))
}

fun BottomSheetDialog.clearDefaultSheetBackground() {
    findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(
        Color.TRANSPARENT
    )
}
