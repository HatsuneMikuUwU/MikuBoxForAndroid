package com.miku.ray.ui.preference.activity

import android.os.Bundle
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.R
import com.miku.ray.ui.base.BaseActivity

class CoreSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_core_settings), subtitle = getString(R.string.mihomo_settings_hint))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, requireNotNull(com.miku.ray.MikuSettings.impl).coreFragment())
            .commit()
        }
    }

}
