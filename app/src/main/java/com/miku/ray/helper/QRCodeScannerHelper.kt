package com.miku.ray.helper

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.king.camera.scan.CameraScan
import com.miku.ray.ui.scanner.QrCaptureActivity

class QRCodeScannerHelper(private val activity: AppCompatActivity) {
    private var scanCallback: ((String?) -> Unit)? = null

    private val scanLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra(CameraScan.SCAN_RESULT)
        } else {
            null
        }
        scanCallback?.invoke(text)
        scanCallback = null
    }

    fun launch(onResult: (String?) -> Unit) {
        scanCallback = onResult
        scanLauncher.launch(Intent(activity, QrCaptureActivity::class.java))
    }
}
