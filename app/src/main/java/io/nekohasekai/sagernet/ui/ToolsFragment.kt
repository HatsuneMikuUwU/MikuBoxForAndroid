package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutToolsBinding

class ToolsFragment : ToolbarFragment(R.layout.layout_tools) {

    private lateinit var binding: LayoutToolsBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = LayoutToolsBinding.bind(view)

        binding.collapsingToolbar.title = getString(R.string.menu_tools)
        binding.toolbar.title = null

        val tools = listOf(
            NetworkFragment(),
            BackupFragment()
        )

        binding.toolsPager.adapter = ToolsAdapter(tools)

        TabLayoutMediator(binding.toolsTab, binding.toolsPager) { tab, position ->
            tab.text = (tools[position] as? NamedFragment)?.name() ?: "Tab $position"
        }.attach()
    }

    inner class ToolsAdapter(private val tools: List<Fragment>) : FragmentStateAdapter(this) {
        override fun getItemCount() = tools.size
        override fun createFragment(position: Int) = tools[position]
    }
}
