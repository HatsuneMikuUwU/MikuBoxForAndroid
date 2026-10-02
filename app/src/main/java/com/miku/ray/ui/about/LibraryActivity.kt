@file:Suppress("DEPRECATION")

package com.miku.ray.ui.about

import com.miku.ray.ui.base.BaseActivity
import android.os.Bundle
import com.mikepenz.aboutlibraries.LibsBuilder
import com.miku.ray.R
import com.miku.ray.databinding.ActivityLibraryBinding

class LibraryActivity : BaseActivity() {

    private val binding by lazy { ActivityLibraryBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupToolbar(binding.toolbar, showHomeAsUp = true, title = getString(R.string.title_oss_license), subtitle = getString(R.string.subtitle_library))
        binding.collapsingToolbar.title = getString(R.string.title_oss_license)

        if (savedInstanceState == null) {
            val fragment = LibsBuilder().withEdgeToEdge(true).supportFragment()
            supportFragmentManager.beginTransaction()
            .replace(binding.libraryFragmentContainer.id, fragment).commit()
        }
    }
}
