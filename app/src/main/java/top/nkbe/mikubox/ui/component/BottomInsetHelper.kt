package top.nkbe.mikubox.ui.component

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnDetach
import androidx.core.view.updatePadding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Tracks the live height of MainActivity's floating bottom dock (StatsBar + FAB + Quick Actions)
 * and system navigation bar insets so scrollable Compose and View lists never get covered.
 */
object MikuInsets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _bottomDockHeightPx = MutableStateFlow(0)
    private val _navBarBottomPx = MutableStateFlow(0)

    val effectiveBottomInsetPx: StateFlow<Int> =
        combine(_bottomDockHeightPx, _navBarBottomPx) { dockPx, navPx ->
            max(dockPx, navPx)
        }.stateIn(scope, SharingStarted.Eagerly, 0)

    fun updateBottomDock(dockHeightPx: Int, navBarBottomPx: Int) {
        _bottomDockHeightPx.value = dockHeightPx.coerceAtLeast(0)
        _navBarBottomPx.value = navBarBottomPx.coerceAtLeast(0)
    }

    fun updateDockVisibilityHeight(dockHeightPx: Int) {
        _bottomDockHeightPx.value = dockHeightPx.coerceAtLeast(0)
    }
}

val LocalMikuBottomDockDp = staticCompositionLocalOf { 0.dp }

@Composable
fun rememberMikuBottomInsetDp(): Dp {
    val px by MikuInsets.effectiveBottomInsetPx.collectAsState()
    return with(LocalDensity.current) { px.toDp() }
}

/**
 * Applies live bottom clearance (floating StatsBar or navigation bar + [extraDp]) to a scrollable View,
 * preserving its original XML bottom padding and avoiding clipping under the dock.
 */
fun View.applyBottomDockPadding(extraDp: Int = 16) {
    if (this is ViewGroup) {
        clipToPadding = false
    }
    val initialBottom = paddingBottom
    val extraPx = (extraDp * resources.displayMetrics.density).roundToInt()

    var localNavBottom = 0
    fun applyNow(insetPx: Int) {
        val targetBottom = initialBottom + max(insetPx, localNavBottom) + extraPx
        if (paddingBottom != targetBottom) {
            updatePadding(bottom = targetBottom)
        }
    }

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        localNavBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        applyNow(MikuInsets.effectiveBottomInsetPx.value)
        insets
    }
    ViewCompat.requestApplyInsets(this)

    val job = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
        MikuInsets.effectiveBottomInsetPx.collect { px ->
            applyNow(px)
        }
    }
    doOnDetach {
        job.cancel()
    }
}
