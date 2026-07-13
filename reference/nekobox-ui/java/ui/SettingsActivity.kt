package io.nekohasekai.sagernet.ui

import android.os.Bundle
import androidx.core.view.ViewCompat
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.widget.ListListener

class SettingsActivity : ThemedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.layout_config_settings)
        setupCollapsingToolbar(R.string.settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings), ListListener)

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, SettingsPreferenceFragment())
            .commitAllowingStateLoss()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun snackbarInternal(text: CharSequence) =
        Snackbar.make(findViewById(R.id.main_content), text, Snackbar.LENGTH_LONG)

}
