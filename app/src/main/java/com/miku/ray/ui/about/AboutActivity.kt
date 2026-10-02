package com.miku.ray.ui.about

import com.miku.ray.ui.base.BaseActivity
import android.content.Intent
import android.os.Bundle
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.BuildConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityAboutBinding
import com.miku.ray.core.CoreNativeManager
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.util.Utils

class AboutActivity : BaseActivity() {
    private val binding by lazy { ActivityAboutBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.aboutContent.applyEdgeToEdgeListInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_about), subtitle = getString(R.string.subtitle_about))

        binding.layoutSoureCcode.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_URL)
        }

        binding.layoutFeedback.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_ISSUES_URL)
        }

        binding.layoutOssLicenses.setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }

        binding.layoutTgChannel.setOnClickListener {
            Utils.openUri(this, AppConfig.TG_CHANNEL_URL)
        }

        binding.layoutPrivacyPolicy.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_PRIVACY_POLICY)
        }

        getString(R.string.version_line_with_core, BuildConfig.VERSION_NAME, CoreNativeManager.getLibVersion()).also {
            binding.tvVersion.text = it
        }
        BuildConfig.APPLICATION_ID.also {
            binding.tvAppId.text = it
        }
    }
}
