package com.miku.ray.ui.perappproxy

import com.miku.ray.ui.base.BaseActivity
import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.AppConfig.ANG_PACKAGE
import com.miku.ray.R
import com.miku.ray.databinding.ActivityBypassListBinding
import com.miku.ray.dto.AppInfo
import com.miku.ray.dto.UrlContentRequest
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.v2RayApplication
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsChangeManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.util.AppManagerUtil
import com.miku.ray.util.HttpUtil
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class PerAppProxyActivity : BaseActivity() {
    private val binding by lazy { ActivityBypassListBinding.inflate(layoutInflater) }
    private var adapter: PerAppProxyAdapter? = null
    private var appsAll: List<AppInfo>? = null
    private val viewModel: PerAppProxyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.recyclerView.applyEdgeToEdgeListInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.per_app_proxy_settings), subtitle = getString(R.string.subtitle_per_app_proxy))

        initList()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.blacklist.collect {
                    adapter?.notifyDataSetChanged()
                }
            }
        }

        binding.chipPerAppProxy.apply {
            isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_PER_APP_PROXY, false)
            setOnCheckedChangeListener { _, isChecked ->
                MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, isChecked)
                // The running tunnel reads the per-app mode when its interface
                // is built; flag the rebuild or the chip change would silently
                // wait for the next reconnect (same contract as the save
                // button and the view model's list updates).
                SettingsChangeManager.makeRestartService()
            }
        }

        binding.chipBypassApps.apply {
            isChecked = MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)
            setOnCheckedChangeListener { _, isChecked ->
                MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, isChecked)
                SettingsChangeManager.makeRestartService()
            }
        }
    }

    private fun initList() {
        showLoading()
        lifecycleScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    val appsList = AppManagerUtil.loadNetworkAppList(this@PerAppProxyActivity)
                    val blacklistSet = viewModel.getAll()

                    if (blacklistSet.isNotEmpty()) {
                        appsList.forEach { app ->
                            app.isSelected = if (blacklistSet.contains(app.packageName)) 1 else 0
                        }
                        appsList.sortedWith { p1, p2 ->
                            when {
                                p1.isSelected > p2.isSelected -> -1
                                p1.isSelected < p2.isSelected -> 1
                                p1.isSystemApp > p2.isSystemApp -> 1
                                p1.isSystemApp < p2.isSystemApp -> -1
                                else -> Collator.getInstance().compare(p1.appName, p2.appName)
                            }
                        }
                    } else {
                        appsList.sortedWith(compareBy(Collator.getInstance()) { it.appName })
                    }
                }

                appsAll = apps
                adapter = PerAppProxyAdapter(apps, viewModel)
                binding.recyclerView.adapter = adapter
            } catch (e: Exception) {
                LogUtil.e(ANG_PACKAGE, "Error loading apps", e)
            } finally {
                hideLoading()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_bypass_list, menu)
        val searchItem = menu.findItem(R.id.search_view)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false
                override fun onQueryTextChange(newText: String?): Boolean {
                    filterProxyApp(newText.orEmpty())
                    return false
                }
        })
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_tips -> {
            snackbarDefault(
                getString(R.string.summary_pref_per_app_proxy),
                title = getString(R.string.title_alerter_info)
            )
            true
        }
        R.id.select_all -> {
            selectAllApp()
            allowPerAppProxy()
            true
        }
        R.id.invert_selection -> {
            invertSelection()
            allowPerAppProxy()
            true
        }
        R.id.select_proxy_app -> {
            selectProxyAppAuto()
            allowPerAppProxy()
            true
        }
        R.id.import_proxy_app -> {
            importProxyApp()
            allowPerAppProxy()
            true
        }
        R.id.export_proxy_app -> {
            exportProxyApp()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun selectAllApp() {
        adapter?.let { adp ->
            val pkgNames = adp.apps.map { it.packageName }
            val allSelected = pkgNames.all { viewModel.contains(it) }
            if (allSelected) viewModel.removeAll(pkgNames) else viewModel.addAll(pkgNames)
            refreshData()
        }
    }

    private fun invertSelection() {
        adapter?.let { adp ->
            adp.apps.forEach { viewModel.toggle(it.packageName) }
            refreshData()
        }
    }

    private fun selectProxyAppAuto() {
        snackbarDefault(R.string.msg_downloading_content, title = getString(R.string.title_alerter_info))
        showLoading()

        val url = AppConfig.ANDROID_PACKAGE_NAME_LIST_URL
        lifecycleScope.launch(Dispatchers.IO) {
            var content = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url,
                    timeout = 5000
                )
            )
            if (content.isNullOrEmpty()) {
                val proxyUsername = SettingsManager.getSocksUsername()
                val proxyPassword = SettingsManager.getSocksPassword()
                val httpPort = SettingsManager.getHttpPort()
                content = HttpUtil.getUrlContent(
                    UrlContentRequest(
                        url = url,
                        timeout = 5000,
                        httpPort = httpPort,
                        proxyUsername = proxyUsername,
                        proxyPassword = proxyPassword
                    )
                ) ?: ""
            }
            launch(Dispatchers.Main) {
                selectProxyApp(content, true)
                snackbarSuccess(
                    getString(R.string.toast_success),
                    title = getString(R.string.title_alerter_success)
                )
                hideLoading()
            }
        }
    }

    private fun importProxyApp() {
        val content = Utils.getClipboard(applicationContext)
        if (!content.isNullOrEmpty()) {
            selectProxyApp(content, false)
            snackbarSuccess(
                getString(R.string.menu_item_import_proxy_app),
                title = getString(R.string.title_alerter_success)
            )
        }
    }

    private fun exportProxyApp() {
        var lst = binding.chipBypassApps.isChecked.toString()
        viewModel.getAll().forEach { pkg ->
            lst += System.lineSeparator() + pkg
        }
        Utils.setClipboard(applicationContext, lst)
        snackbarSuccess(
            getString(R.string.menu_item_export_proxy_app),
            title = getString(R.string.title_alerter_success)
        )
    }

    private fun allowPerAppProxy() {
        binding.chipPerAppProxy.isChecked = true
        SettingsChangeManager.makeRestartService()
    }

    private fun selectProxyApp(content: String, force: Boolean): Boolean {
        try {
            val proxyApps = content.ifEmpty { Utils.readTextFromAssets(v2RayApplication, "proxy_package_name") }
            if (proxyApps.isEmpty()) return false

            viewModel.clear()
            val isBypassMode = binding.chipBypassApps.isChecked

            adapter?.apps?.forEach { app ->
                val inList = inProxyApps(proxyApps, app.packageName, force)
                if (isBypassMode) {
                    if (!inList) viewModel.add(app.packageName)
                } else {
                    if (inList) viewModel.add(app.packageName)
                }
            }
            refreshData()
        } catch (e: Exception) {
            return false
        }
        return true
    }

    private fun inProxyApps(proxyApps: String, packageName: String, force: Boolean): Boolean {
        if (force) {
            if (packageName == "com.google.android.webview") return false
            if (packageName.startsWith("com.google")) return true
        }
        return proxyApps.contains(packageName)
    }

    private fun filterProxyApp(content: String): Boolean {
        val key = content.uppercase()
        val filtered = if (key.isEmpty()) {
            appsAll.orEmpty()
        } else {
            appsAll?.filter { it.appName.uppercase().contains(key) || it.packageName.uppercase().contains(key) }.orEmpty()
        }
        adapter = PerAppProxyAdapter(filtered, viewModel)
        binding.recyclerView.adapter = adapter
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        adapter?.notifyDataSetChanged()
    }
}
