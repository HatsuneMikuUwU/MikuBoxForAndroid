package com.miku.ray.ui.userasset

import com.miku.ray.ui.base.BaseActivity
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import com.miku.ray.util.showDeleteConfirmDialog
import com.google.android.material.appbar.MaterialToolbar
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityUserAssetUrlBinding
import com.miku.ray.dto.entities.AssetUrlItem
import com.miku.ray.extension.applyEdgeToEdgeListInsets
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.extension.toastSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils
import java.io.File

class UserAssetUrlActivity : BaseActivity() {
    companion object {
        const val ASSET_URL_QRCODE = "ASSET_URL_QRCODE"
    }

    private val binding by lazy { ActivityUserAssetUrlBinding.inflate(layoutInflater) }

    private var del_config: MenuItem? = null
    private var save_config: MenuItem? = null

    private val extDir by lazy { File(Utils.userAssetPath(this)) }
    private val editAssetId by lazy { intent.getStringExtra("assetId").orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        binding.userAssetUrlScrollContent.applyEdgeToEdgeListInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setupToolbar(toolbar, showHomeAsUp = true, title = getString(R.string.title_user_asset_add_url), subtitle = getString(R.string.subtitle_user_asset_url))

        val assetItem = MmkvManager.decodeAsset(editAssetId)
        val assetUrlQrcode = intent.getStringExtra(ASSET_URL_QRCODE)
        val assetNameQrcode = assetUrlQrcode?.let { File(it).name }

        when {
            assetItem != null -> bindingAsset(assetItem)
            assetUrlQrcode != null -> {
                binding.etRemarks.setText(assetNameQrcode)
                binding.etUrl.setText(assetUrlQrcode)
            }
            else -> clearAsset()
        }
    }

    private fun bindingAsset(assetItem: AssetUrlItem): Boolean {
        binding.etRemarks.setText(Utils.getEditable(assetItem.remarks))
        binding.etUrl.setText(Utils.getEditable(assetItem.url))
        return true
    }

    private fun clearAsset(): Boolean {
        binding.etRemarks.text = null
        binding.etUrl.text = null
        return true
    }

    private fun saveServer(): Boolean {
        var assetItem = MmkvManager.decodeAsset(editAssetId)
        var assetId = editAssetId

        if (assetItem != null) {
            val file = extDir.resolve(assetItem.remarks)
            if (file.exists()) {
                try {
                    file.delete()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to delete asset file: ${file.path}", e)
                }
            }
        } else {
            assetId = Utils.getUuid()
            assetItem = AssetUrlItem()
        }

        assetItem.remarks = binding.etRemarks.text?.toString().orEmpty()
        assetItem.url = binding.etUrl.text?.toString().orEmpty()

        val assetList = MmkvManager.decodeAssetUrls()
        if (assetList.any { it.assetUrl.remarks == assetItem.remarks && it.guid != assetId }) {
            snackbarError(
                getString(R.string.msg_remark_is_duplicate),
                title = getString(R.string.title_alerter_error)
            )
            return false
        }

        if (TextUtils.isEmpty(assetItem.remarks)) {
            snackbarError(
                getString(R.string.sub_setting_remarks),
                title = getString(R.string.title_alerter_error)
            )
            return false
        }
        if (TextUtils.isEmpty(assetItem.url)) {
            snackbarError(
                getString(R.string.title_url),
                title = getString(R.string.title_alerter_error)
            )
            return false
        }

        MmkvManager.encodeAsset(assetId, assetItem)
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer(): Boolean {
        if (editAssetId.isNotEmpty()) {
            showDeleteConfirmDialog(context = this, messageRes = R.string.del_file_asset_dialog_comfirm_message) {
                MmkvManager.removeAssetUrl(editAssetId)
                finish()
            }
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        del_config = menu.findItem(R.id.del_config)
        save_config = menu.findItem(R.id.save_config)

        if (editAssetId.isEmpty()) {
            del_config?.isVisible = false
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.del_config -> {
            deleteServer()
            true
        }

        R.id.save_config -> {
            saveServer()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

}
