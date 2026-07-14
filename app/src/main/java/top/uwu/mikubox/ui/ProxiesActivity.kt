package top.uwu.mikubox.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.uwu.mikubox.R
import top.uwu.mikubox.core.MihomoCore
import top.uwu.mikubox.databinding.ActivityProxiesBinding
import top.uwu.mikubox.service.VpnController

/**
 * ClashMetaForAndroid-style node manager: lists the selector groups of the running
 * core and their member nodes, allowing selection and latency testing. Nodes are
 * only available while the core is running, so this screen requires a connection.
 */
class ProxiesActivity : EdgeToEdgeActivity() {

    private lateinit var binding: ActivityProxiesBinding
    private val adapter = ProxyNodeAdapter(onSelect = ::selectNode)

    private var allProxies: Map<String, MihomoCore.Proxy> = emptyMap()
    private var groups: List<MihomoCore.Proxy> = emptyList()
    private val delayCache = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProxiesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_test_delay) {
                testCurrentGroup()
                true
            } else {
                false
            }
        }

        binding.rvNodes.layoutManager = LinearLayoutManager(this)
        binding.rvNodes.adapter = adapter

        binding.groupTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = renderGroup(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        load()
    }

    private fun load() {
        if (!VpnController.isRunning) {
            showEmpty(R.string.proxies_empty_disconnected)
            return
        }
        lifecycleScope.launch {
            allProxies = withContext(Dispatchers.IO) { MihomoCore.proxies() }
            groups = allProxies.values.filter { it.isGroup && it.isSelector }
            if (groups.isEmpty()) {
                showEmpty(R.string.proxies_empty_none)
                return@launch
            }
            binding.tvEmpty.visibility = android.view.View.GONE
            binding.rvNodes.visibility = android.view.View.VISIBLE
            binding.cardGroups.visibility = android.view.View.VISIBLE
            buildTabs()
        }
    }

    private fun buildTabs() {
        val tabs = binding.groupTab
        tabs.removeAllTabs()
        groups.forEach { group -> tabs.addTab(tabs.newTab().setText(group.name)) }
        renderGroup(0)
    }

    private fun renderGroup(index: Int) {
        val group = groups.getOrNull(index) ?: return
        val nodes = group.all.map { memberName ->
            val info = allProxies[memberName]
            ProxyNodeAdapter.Node(
                name = memberName,
                type = info?.type ?: "",
                delay = delayCache[memberName] ?: (info?.delay ?: 0).let { if (it > 0) it else -2 },
                selected = memberName == group.now,
            )
        }
        adapter.submit(nodes)
    }

    private fun selectNode(nodeName: String) {
        val index = binding.groupTab.selectedTabPosition
        val group = groups.getOrNull(index) ?: return
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { MihomoCore.selectProxy(group.name, nodeName) }
            if (ok) {
                allProxies = withContext(Dispatchers.IO) { MihomoCore.proxies() }
                groups = allProxies.values.filter { it.isGroup && it.isSelector }
                renderGroup(index)
                toast(getString(R.string.toast_node_selected, nodeName))
            } else {
                toast(getString(R.string.toast_node_select_failed))
            }
        }
    }

    private fun testCurrentGroup() {
        val index = binding.groupTab.selectedTabPosition
        val group = groups.getOrNull(index) ?: return
        val members = group.all
        if (members.isEmpty()) return
        toast(getString(R.string.proxies_testing))
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                members.map { name -> async { name to MihomoCore.delay(name) } }
                    .awaitAll()
                    .forEach { (name, result) -> delayCache[name] = if (result < 0) -1 else result }
            }
            renderGroup(index)
        }
    }

    private fun showEmpty(messageRes: Int) {
        binding.tvEmpty.setText(messageRes)
        binding.tvEmpty.visibility = android.view.View.VISIBLE
        binding.rvNodes.visibility = android.view.View.GONE
        binding.cardGroups.visibility = android.view.View.GONE
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
