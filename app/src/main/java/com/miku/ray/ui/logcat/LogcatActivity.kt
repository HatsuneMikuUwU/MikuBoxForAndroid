package com.miku.ray.ui.logcat

import com.miku.ray.ui.base.BaseActivity
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.R
import com.miku.ray.databinding.ActivityLogcatBinding
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.util.Utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogcatActivity : BaseActivity(), SwipeRefreshLayout.OnRefreshListener {
    private val binding by lazy { ActivityLogcatBinding.inflate(layoutInflater) }
    private val viewModel: LogcatViewModel by viewModels()
    private lateinit var adapter: LogcatRecyclerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.recyclerView.applyEdgeToEdgeListInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_logcat), subtitle = getString(R.string.subtitle_logcat))

        adapter = LogcatRecyclerAdapter(viewModel, ::onLogLongClick)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logs.collect {
                    adapter.notifyDataSetChanged()
                }
            }
        }

        binding.refreshLayout.setOnRefreshListener(this)

        // F32: the screen used to open empty with only a "pull down to
        // refresh" banner. Load the log once on entry instead; the swipe
        // handler stays for manual refreshes. Pulling again on every resume
        // was rejected — onCreate only.
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadLogcat()
            withContext(Dispatchers.Main) {
                refreshData()
            }
        }
    }

    private fun onLogLongClick(log: String): Boolean {
        Utils.setClipboard(this, log)
        return true
    }

    private fun shareLogcat() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logText = viewModel.getAll().joinToString("\n")

            val result = try {
                val shareDir = File(cacheDir, "shared_logs").apply {
                    mkdirs()
                }

                shareDir.listFiles()?.forEach { it.delete() }

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val logFile = File(shareDir, "MikuRay_logcat_$timestamp.txt")
                logFile.writeText(logText, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    this@LogcatActivity,
                    "${packageName}.cache",
                    logFile
                )

                uri to logFile.name
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarDefault(e.localizedMessage ?: e.toString(), title = getString(R.string.title_alerter_info))
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, result.first)
                    putExtra(Intent.EXTRA_SUBJECT, result.second)
                    putExtra(Intent.EXTRA_TITLE, result.second)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(contentResolver, result.second, result.first)
                }

                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        getString(R.string.logcat_share)
                    )
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_logcat, menu)

        val searchItem = menu.findItem(R.id.search_view)
        if (searchItem != null) {
            val searchView = searchItem.actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean = false

                    override fun onQueryTextChange(newText: String?): Boolean {
                        viewModel.filter(newText)
                        refreshData()
                        return false
                    }
            })
            searchView.setOnCloseListener {
                viewModel.filter("")
                refreshData()
                false
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.copy_all -> {
            val all = viewModel.getAll().joinToString("\n")
            Utils.setClipboard(this, all)
            snackbarSuccess(
                getString(R.string.logcat_copy),
                title = getString(R.string.title_alerter_success)
            )
            true
        }

        R.id.share_all -> {
            shareLogcat()
            true
        }

        R.id.clear_all -> {
            lifecycleScope.launch(Dispatchers.IO) {
                viewModel.clearLogcat()
                withContext(Dispatchers.Main) {
                    refreshData()
                }
            }
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    override fun onRefresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadLogcat()
            withContext(Dispatchers.Main) {
                binding.refreshLayout.isRefreshing = false
                refreshData()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshData() {
        adapter.notifyDataSetChanged()
    }
}
