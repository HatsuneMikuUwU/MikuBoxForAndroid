package com.miku.ray.ui.base

import android.net.Uri
import android.os.Bundle
import com.miku.ray.enums.PermissionType
import com.miku.ray.helper.FileChooserHelper
import com.miku.ray.helper.PermissionHelper
import com.miku.ray.helper.QRCodeScannerHelper

abstract class HelperBaseActivity : BaseActivity() {
    private lateinit var fileChooser: FileChooserHelper
    private lateinit var permissionRequester: PermissionHelper
    private lateinit var qrCodeScanner: QRCodeScannerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileChooser = FileChooserHelper(this)
        permissionRequester = PermissionHelper(this)
        qrCodeScanner = QRCodeScannerHelper(this)
    }

    protected fun checkAndRequestPermission(
        permissionType: PermissionType,
        onGranted: () -> Unit
    ) {
        permissionRequester.request(permissionType, onGranted)
    }

    protected fun launchFileChooser(
        mimeType: String = "*/*",
        extraMimeTypes: Array<String>? = null,
        onResult: (Uri?) -> Unit
    ) {
        fileChooser.launch(mimeType, extraMimeTypes, onResult)
    }

    protected fun launchCreateDocument(
        fileName: String,
        onResult: (Uri?) -> Unit
    ) {
        fileChooser.createDocument(fileName, onResult)
    }

    protected fun launchQRCodeScanner(onResult: (String?) -> Unit) {
        checkAndRequestPermission(PermissionType.CAMERA) {
            qrCodeScanner.launch(onResult)
        }
    }
}
