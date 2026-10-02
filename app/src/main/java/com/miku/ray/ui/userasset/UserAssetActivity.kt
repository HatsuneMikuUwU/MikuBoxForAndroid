package com.miku.ray.ui.userasset

import com.miku.ray.ui.base.HelperBaseActivity
import com.miku.ray.util.showDeleteConfirmDialog
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.contracts.BaseAdapterListener
import com.miku.ray.databinding.ActivityUserAssetBinding
import com.miku.ray.dto.entities.AssetUrlItem
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastInfo
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.ui.bottomsheet.AssetMenuBottomSheet
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class UserAssetActivity : HelperBaseActivity(), AssetMenuBottomSheet.OnAssetMenuOptionClickListener {
    private val binding by lazy { ActivityUserAssetBinding.inflate(layoutInflater) }
    private val ownerActivity: UserAssetActivity
    get() = this
    private val viewModel: UserAssetViewModel by viewModels()
    private lateinit var adapter: UserAssetAdapter

    private val extDir by lazy { File(Utils.userAssetPath(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.userAssetScrollContent.applyEdgeToEdgeListInsets()
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_user_asset_setting), subtitle = getString(R.string.subtitle_user_asset))

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UserAssetAdapter(viewModel, ActivityAdapterListener())
        binding.recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_asset, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.download_file -> downloadGeoFiles().let { true }
        R.id.action_more_menu -> {
            val bottomSheet = AssetMenuBottomSheet()
            bottomSheet.show(supportFragmentManager, AssetMenuBottomSheet.TAG)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onAssetMenuOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.add_file -> showFileChooser()
            R.id.add_url -> startActivity(Intent(this, UserAssetUrlActivity::class.java))
            R.id.add_qrcode -> importAssetFromQRcode()
        }
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }

            val assetId = Utils.getUuid()
            runCatching {
                val assetItem = AssetUrlItem(
                    getCursorName(uri) ?: uri.toString(),
                    "file"
                )

                val assetList = MmkvManager.decodeAssetUrls()
                if (assetList.any { it.assetUrl.remarks == assetItem.remarks && it.guid != assetId }) {
                    snackbarDefault(R.string.msg_remark_is_duplicate, title = getString(R.string.title_alerter_info))
                } else {
                    MmkvManager.encodeAsset(assetId, assetItem)
                    copyFile(uri)
                }
            }.onFailure {
                snackbarError(
                    getString(R.string.toast_asset_copy_failed),
                    title = getString(R.string.title_alerter_error)
                )
                MmkvManager.removeAssetUrl(assetId)
            }
        }
    }

    private fun copyFile(uri: Uri): String {
        val targetFile = File(extDir, getCursorName(uri) ?: uri.toString())
        contentResolver.openInputStream(uri).use { inputStream ->
            targetFile.outputStream().use { fileOut ->
                inputStream?.copyTo(fileOut)
                snackbarSuccess(
                    getString(R.string.menu_item_add_file),
                    title = getString(R.string.title_alerter_success)
                )
                refreshData()
            }
        }
        return targetFile.path
    }

    private fun getCursorName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.let { cursor ->
            cursor.run {
                if (moveToFirst()) getString(getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else null
            }.also { cursor.close() }
        }
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to get cursor name", e)
        null
    }

    private fun importAssetFromQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importAsset(scanResult)
            }
        }
        return true
    }

    private fun importAsset(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                snackbarDefault(R.string.toast_invalid_url, title = getString(R.string.title_alerter_info))
                return false
            }
            startActivity(
                Intent(this, UserAssetUrlActivity::class.java)
                .putExtra(UserAssetUrlActivity.ASSET_URL_QRCODE, url)
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import asset from URL", e)
            return false
        }
        return true
    }

    private fun downloadGeoFiles() {
        showLoading()
        toastInfo(R.string.msg_downloading_content)

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        lifecycleScope.launch {
            reloadDataAndAwait()

            val result = withContext(Dispatchers.IO) {
                viewModel.downloadGeoFiles(extDir, httpPort, proxyUsername, proxyPassword)
            }

            if (result.successCount > 0) {
                snackbarSuccess(
                    getString(R.string.title_update_asset_count, result.successCount),
                    title = getString(R.string.title_alerter_success)
                )
            } else {
                snackbarError(
                    getString(R.string.menu_item_download_file),
                    title = getString(R.string.title_alerter_error)
                )
            }

            reloadDataAndAwait()
            hideLoading()
        }
    }

    private fun initAssets() {
        lifecycleScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(this@UserAssetActivity, assets)
            withContext(Dispatchers.Main) {
                reloadDataAndAwait()
            }
        }
    }

    private fun refreshData() {
        lifecycleScope.launch {
            reloadDataAndAwait()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private suspend fun reloadDataAndAwait() {
        val geoFilesSource = MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES)
        ?: AppConfig.GEO_FILES_SOURCES.first()
        viewModel.reload(geoFilesSource, extDir).join()
        adapter.notifyDataSetChanged()
    }

    private inner class ActivityAdapterListener : BaseAdapterListener {
        override fun onEdit(guid: String, position: Int) {
            startActivity(
                Intent(ownerActivity, UserAssetUrlActivity::class.java)
                .putExtra("assetId", guid)
            )
        }

        override fun onRemove(guid: String, position: Int) {
            val asset = viewModel.uiState.value.assets.getOrNull(position)?.takeIf { it.guid == guid }
            ?: viewModel.uiState.value.assets.find { it.guid == guid }
            ?: return
            val file = File(extDir, asset.assetUrl.remarks)

            showDeleteConfirmDialog(context = ownerActivity, messageRes = R.string.del_file_asset_dialog_comfirm_message) {
                file.delete()
                MmkvManager.removeAssetUrl(guid)
                initAssets()
                snackbarSuccess(
                    message = getString(R.string.toast_delete_success),
                    title = asset.assetUrl.remarks
                )
            }
        }

        override fun onShare(url: String) {
        }

        override fun onRefreshData() {
            refreshData()
        }
    }
}
