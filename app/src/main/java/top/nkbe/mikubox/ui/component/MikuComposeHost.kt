package top.nkbe.mikubox.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import top.nkbe.mikubox.ui.theme.MikuBoxTheme

/**
 * Creates a lifecycle-aware ComposeView wrapped in [MikuBoxTheme] for hybrid Fragment/Compose migration.
 */
fun Fragment.mikuComposeView(
    content: @Composable () -> Unit,
): ComposeView = ComposeView(requireContext()).apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        MikuBoxTheme(content = content)
    }
}
