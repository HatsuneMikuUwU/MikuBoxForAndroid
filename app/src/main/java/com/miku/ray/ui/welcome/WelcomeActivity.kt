package com.miku.ray.ui.welcome

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.splash.SplashActivity

class WelcomeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (MmkvManager.decodeSettingsBool(PREF_WELCOME_COMPLETED, false)) {
            navigateToMain()
            return
        }

        setContentView(R.layout.uwu_activity_welcome)

        val rootLayout = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }

        setupViewsAndListeners()
    }

    private fun setupViewsAndListeners() {
        pages = listOf(
            findViewById(R.id.page1),
            findViewById(R.id.page2),
            findViewById(R.id.page3),
        )

        pages.forEachIndexed { index, page -> page.visibility = if (index == 0) View.VISIBLE else View.GONE }

        // The guide had no swipe at all — the pages are static views toggled
        // by visibility, not a pager — so a horizontal fling used to do
        // nothing. Drive the same page switches from a gesture detector; the
        // buttons keep working because child views consume their own taps
        // before the root ever sees them.
        val gestureDetector = androidx.core.view.GestureDetectorCompat(
            this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                private var gestureStartX = 0f
                private var gestureStartY = 0f
                private var flippedThisGesture = false

                override fun onDown(event: android.view.MotionEvent): Boolean {
                    gestureStartX = event.x
                    gestureStartY = event.y
                    flippedThisGesture = false
                    return true
                }

                override fun onFling(
                    start: android.view.MotionEvent?,
                    end: android.view.MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (start == null || flippedThisGesture) return false
                    val dx = end.x - start.x
                    val dy = end.y - start.y
                    val minDistance = SWIPE_MIN_DISTANCE_DP * resources.displayMetrics.density
                    val minVelocity = SWIPE_MIN_VELOCITY_DP * resources.displayMetrics.density
                    if (kotlin.math.abs(dx) < minDistance || kotlin.math.abs(dx) <= kotlin.math.abs(dy)) return false
                    if (kotlin.math.abs(velocityX) < minVelocity) return false
                    // Swiping left (negative dx) advances, right goes back —
                    // like every pager. Edges clamp: the first page has no
                    // left neighbour, the last one no right neighbour.
                    flippedThisGesture = flipBy(dx)
                    return flippedThisGesture
                }

                override fun onScroll(
                    start: android.view.MotionEvent?,
                    end: android.view.MotionEvent,
                    distanceX: Float,
                    distanceY: Float,
                ): Boolean {
                    if (start == null || flippedThisGesture) return false
                    // A slow deliberate drag produces no fling event; half a
                    // screen of horizontal travel still flips the page.
                    val draggedX = end.x - gestureStartX
                    val draggedY = end.y - gestureStartY
                    val halfWidth = resources.displayMetrics.widthPixels / 2f
                    if (kotlin.math.abs(draggedX) >= halfWidth && kotlin.math.abs(draggedX) > kotlin.math.abs(draggedY)) {
                        flippedThisGesture = flipBy(draggedX)
                        return flippedThisGesture
                    }
                    return false
                }
            },
        )
        findViewById<View>(R.id.main_content).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            // Must consume: a `false` return tells the framework this view did
            // not take the gesture, so MOVE/UP never come back and the
            // detector can never assemble a swipe — that starved stream was
            // exactly why the first implementation had no effect. Children
            // (buttons) already consumed their own touches during dispatch.
            true
        }

        findViewById<View>(R.id.page_1button).setOnClickListener { showPage(1) }
        findViewById<View>(R.id.page_2button).setOnClickListener { showPage(2) }

        val navigateAction = View.OnClickListener { navigateToMain() }

        findViewById<View>(R.id.page_3button).setOnClickListener(navigateAction)
        findViewById<View>(R.id.page_1_skip).setOnClickListener(navigateAction)
        findViewById<View>(R.id.page_2_skip).setOnClickListener(navigateAction)
    }

    /** Direction-aware flip for gestures: dx < 0 advances. True when it flipped. */
    private fun flipBy(dx: Float): Boolean {
        val target = if (dx < 0) (currentPage + 1).coerceAtMost(pages.lastIndex) else (currentPage - 1).coerceAtLeast(0)
        val changed = target != currentPage
        showPage(target)
        return changed
    }

    /** Flips the static guide pages by visibility; the shared swap for taps and swipes. */
    private fun showPage(index: Int) {
        if (index !in pages.indices || index == currentPage) return
        pages.forEachIndexed { current, page -> page.visibility = if (current == index) View.VISIBLE else View.GONE }
        currentPage = index
    }

    private var pages: List<View> = emptyList()
    private var currentPage = 0

    private fun navigateToMain() {
        MmkvManager.encodeSettings(PREF_WELCOME_COMPLETED, true)
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }

    companion object {
        // Do not inherit the old port's completion flag: MikuBox's introduction
        // must be shown once even when these settings already exist.
        private const val PREF_WELCOME_COMPLETED = "pref_mikubox_welcome_completed"

        // Horizontal-swipe thresholds for the guide pages. Calibrated on the
        // emulator (density 2.75): a short 130 px flick covers ~47 dp at
        // ~525 dp/s (90 ms) down to ~118 dp/s (400 ms), so both bounds must
        // sit below that — 40 dp and 100 dp/s — while still ignoring
        // sub-110 px jitter.
        const val SWIPE_MIN_DISTANCE_DP = 40f
        const val SWIPE_MIN_VELOCITY_DP = 100f
    }
}
