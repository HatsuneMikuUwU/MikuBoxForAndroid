package com.miku.ray.helper

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.ui.backup.BackupManager
import com.miku.ray.util.LogUtil

class FileChooserHelper(private val activity: AppCompatActivity) {
    private var fileChooserCallback: ((Uri?) -> Unit)? = null
    private var documentCreateCallback: ((Uri?) -> Unit)? = null

    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == AppCompatActivity.RESULT_OK && uri != null) {
                fileChooserCallback?.invoke(uri)
            } else {
                fileChooserCallback?.invoke(null)
            }
            fileChooserCallback = null
        }

    private val documentCreateLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE)
        ) { uri ->
            documentCreateCallback?.invoke(uri)
            documentCreateCallback = null
        }

    fun launch(
        mimeType: String = "*/*",
        extraMimeTypes: Array<String>? = null,
        onResult: (Uri?) -> Unit
    ) {
        fileChooserCallback = onResult

        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            if (extraMimeTypes.isNullOrEmpty()) {
                type = mimeType
            } else {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(mimeType) + extraMimeTypes)
            }
            addCategory(Intent.CATEGORY_OPENABLE)
        }

        try {
            fileChooserLauncher.launch(
                Intent.createChooser(intent, activity.getString(R.string.title_file_chooser))
            )
        } catch (ex: ActivityNotFoundException) {
            LogUtil.e(AppConfig.TAG, "File chooser activity not found", ex)
            activity.snackbarDefault(
                R.string.toast_require_file_manager,
                title = activity.getString(R.string.title_alerter_info)
            )
            fileChooserCallback?.invoke(null)
            fileChooserCallback = null
        }
    }

    fun createDocument(
        fileName: String,
        onResult: (Uri?) -> Unit
    ) {
        documentCreateCallback = onResult
        try {
            documentCreateLauncher.launch(fileName)
        } catch (ex: ActivityNotFoundException) {
            LogUtil.e(AppConfig.TAG, "Document creator activity not found", ex)
            activity.snackbarDefault(
                R.string.toast_require_file_manager,
                title = activity.getString(R.string.title_alerter_info)
            )
            documentCreateCallback?.invoke(null)
            documentCreateCallback = null
        }
    }
}
