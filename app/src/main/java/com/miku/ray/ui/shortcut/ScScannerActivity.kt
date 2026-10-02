package com.miku.ray.ui.shortcut
import com.miku.ray.ui.base.HelperBaseActivity
import com.miku.ray.ui.main.MainActivity

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.miku.ray.R
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.util.requestSubscriptionImportName

class ScScannerActivity : HelperBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_none)
        importQRcode()
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                lifecycleScope.launch {
                    val (count, countSub) = AngConfigManager.importBatchConfig(
                        scanResult,
                        "",
                        false
                    ) { suggested, existing ->
                        requestSubscriptionImportName(suggested, existing)
                    }
                    if (count + countSub > 0) {
                        snackbarSuccess(R.string.toast_success, title = getString(R.string.title_alerter_success))
                    } else {
                        snackbarError(R.string.toast_failure, title = getString(R.string.title_alerter_error))
                    }
                    startActivity(Intent(this@ScScannerActivity, MainActivity::class.java))
                    finish()
                }
            } else {
                finish()
            }
        }
    }
}
