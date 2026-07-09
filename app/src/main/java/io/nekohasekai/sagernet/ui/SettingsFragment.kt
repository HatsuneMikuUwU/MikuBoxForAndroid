package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutSettingsBinding

class SettingsFragment : ToolbarFragment(R.layout.layout_settings) {

    private lateinit var binding: LayoutSettingsBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutSettingsBinding.bind(view)
        binding.collapsingToolbar.title = getString(R.string.settings)
        binding.toolbar.title = null

        val pages = listOf(
            SettingsPreferenceFragment(),
            ThemeSettingsPreferenceFragment()
        )

        binding.settingsPager.adapter = SettingsAdapter(pages)
        binding.settingsPager.offscreenPageLimit = pages.size

        TabLayoutMediator(binding.settingsTab, binding.settingsPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.settings)
                1 -> getString(R.string.uwu_miku_ui)
                else -> "Tab $position"
            }
        }.attach()
    }

    private inner class SettingsAdapter(
        private val pages: List<Fragment>
    ) : FragmentStateAdapter(this) {
        override fun getItemCount() = pages.size

        override fun createFragment(position: Int): Fragment = pages[position]
    }
}
