package com.miku.ray.ui.scanner

import android.content.Intent
import com.google.zxing.Result
import com.king.camera.scan.analyze.Analyzer
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.CameraScan
import com.king.zxing.BarcodeCameraScanActivity
import com.king.zxing.analyze.QRCodeAnalyzer

class QrCaptureActivity : BarcodeCameraScanActivity() {

    override fun initCameraScan(cameraScan: CameraScan<Result>) {
        super.initCameraScan(cameraScan)
        cameraScan.setPlayBeep(false)
    }

    override fun createAnalyzer(): Analyzer<Result> {
        return QRCodeAnalyzer()
    }

    override fun onScanResultCallback(result: AnalyzeResult<Result>) {
        cameraScan.setAnalyzeImage(false)
        val intent = Intent().putExtra(CameraScan.SCAN_RESULT, result.result.text)
        setResult(RESULT_OK, intent)
        finish()
    }
}
