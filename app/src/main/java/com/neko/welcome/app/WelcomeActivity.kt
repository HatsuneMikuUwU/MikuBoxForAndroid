package com.neko.welcome.app

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neko.splash.app.SplashActivity
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.R
import java.util.*

class WelcomeActivity : ThemedActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var dialog: MaterialAlertDialogBuilder

    private lateinit var page1: LinearLayout
    private lateinit var page2: LinearLayout
    private lateinit var page3: LinearLayout
    private lateinit var page1Button: MaterialButton
    private lateinit var page2Button: MaterialButton
    private lateinit var page3Button: MaterialButton
    private lateinit var page1TextView: TextView
    private lateinit var page2TextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.uwu_activity_welcome)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        if (isNightMode) {
            windowInsetsController.isAppearanceLightStatusBars = false
            windowInsetsController.isAppearanceLightNavigationBars = false
        } else {
            windowInsetsController.isAppearanceLightStatusBars = true
            windowInsetsController.isAppearanceLightNavigationBars = true
        }

        prefs = getSharedPreferences("f", MODE_PRIVATE)
        dialog = MaterialAlertDialogBuilder(this)

        initViews()
        setupListeners()
        handleWelcomeLogic()
    }

    private fun initViews() {
        page1 = findViewById(R.id.page1)
        page2 = findViewById(R.id.page2)
        page3 = findViewById(R.id.page3)

        page1Button = findViewById(R.id.page_1button)
        page2Button = findViewById(R.id.page_2button)
        page3Button = findViewById(R.id.page_3button)

        page1TextView = findViewById(R.id.page_1textview)
        page2TextView = findViewById(R.id.page_2textview)
    }

    private fun setupListeners() {
        page1Button.setOnClickListener {
            page1.visibility = View.GONE
            page2.visibility = View.VISIBLE
        }

        page2Button.setOnClickListener {
            page2.visibility = View.GONE
            page3.visibility = View.VISIBLE
        }

        page3Button.setOnClickListener {
            navigateToSplash()
        }

        page1TextView.setOnClickListener {
            navigateToSplash()
        }

        page2TextView.setOnClickListener {
            navigateToSplash()
        }
    }

    private fun handleWelcomeLogic() {
        when (prefs.getString("f", "")) {
            "null" -> {
                navigateToSplash()
            }
            else -> {
                prefs.edit().putString("f", "true").apply()
                if (prefs.getString("f", "") == "true") {
                    prefs.edit().putString("f", "false").apply()
                }
                if (prefs.getString("f", "") == "false") {
                    prefs.edit().putString("f", "null").apply()
                }
            }
        }
    }

    private fun navigateToSplash() {
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }

    // Deprecated utility functions below...

    @Deprecated("Use ViewBinding or RecyclerView for item selection.")
    fun getCheckedItemPositionsToArray(listView: ListView): ArrayList<Double> {
        val result = ArrayList<Double>()
        val checked = listView.checkedItemPositions
        for (i in 0 until checked.size()) {
            if (checked.valueAt(i)) result.add(checked.keyAt(i).toDouble())
        }
        return result
    }

    @Deprecated("Use resources.getDimension or dp to px extensions.")
    fun getDip(value: Int): Float =
        resources.displayMetrics.density * value

    @Deprecated("Use WindowMetrics for modern display metrics.")
    fun getDisplayHeightPixels(): Int =
        resources.displayMetrics.heightPixels

    @Deprecated("Use WindowMetrics for modern display metrics.")
    fun getDisplayWidthPixels(): Int =
        resources.displayMetrics.widthPixels

    @Deprecated("Use view.x or view.translationX.")
    fun getLocationX(view: View): Int {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        return loc[0]
    }

    @Deprecated("Use view.y or view.translationY.")
    fun getLocationY(view: View): Int {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        return loc[1]
    }

    @Deprecated("Use kotlin.random.Random for randomness.")
    fun getRandom(min: Int, max: Int): Int =
        Random().nextInt(max - min + 1) + min

    @Deprecated("Use Snackbar or Log for user feedback.")
    fun showMessage(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
