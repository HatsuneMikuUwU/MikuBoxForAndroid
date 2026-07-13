package top.uwu.mikubox.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.uwu.mikubox.databinding.ActivityAppListBinding
import top.uwu.mikubox.service.MihomoVpnSettings
import top.uwu.mikubox.service.MihomoVpnSettings.AppMode

/** Per-app proxy picker, wired to [MihomoVpnSettings] (mode + package set). */
class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var adapter: AppListAdapter
    private val selected = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        selected.addAll(MihomoVpnSettings.packages(this))

        adapter = AppListAdapter(packageManager) { pkg, checked ->
            if (checked) selected.add(pkg) else selected.remove(pkg)
            MihomoVpnSettings.setPackages(this, selected)
        }
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter

        when (MihomoVpnSettings.appMode(this)) {
            AppMode.ALL -> binding.modeGroup.check(binding.modeAll.id)
            AppMode.ALLOW_LIST -> binding.modeGroup.check(binding.modeAllow.id)
            AppMode.DISALLOW_LIST -> binding.modeGroup.check(binding.modeDisallow.id)
        }
        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                binding.modeAllow.id -> AppMode.ALLOW_LIST
                binding.modeDisallow.id -> AppMode.DISALLOW_LIST
                else -> AppMode.ALL
            }
            MihomoVpnSettings.setAppMode(this, mode)
            adapter.setEnabled(mode != AppMode.ALL)
        }

        loadApps()
    }

    private fun loadApps() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) { installedApps() }
            adapter.submit(apps, selected, MihomoVpnSettings.appMode(this@AppListActivity) != AppMode.ALL)
            binding.progress.visibility = View.GONE
        }
    }

    private fun installedApps(): List<AppListAdapter.AppItem> {
        val self = packageName
        return packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .asSequence()
            .filter { it.packageName != self }
            .filter { it.requestedPermissions?.contains(android.Manifest.permission.INTERNET) == true }
            .map { pkg ->
                val info = pkg.applicationInfo!!
                AppListAdapter.AppItem(
                    packageName = pkg.packageName,
                    label = info.loadLabel(packageManager).toString(),
                    info = info,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
