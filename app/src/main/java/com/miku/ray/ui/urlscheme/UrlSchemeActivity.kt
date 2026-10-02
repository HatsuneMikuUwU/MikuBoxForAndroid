package com.miku.ray.ui.urlscheme

import com.miku.ray.ui.base.BaseActivity
import com.miku.ray.ui.main.MainActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.databinding.ActivityLogcatBinding
import com.miku.ray.extension.snackbarDefault
import com.miku.ray.extension.snackbarError
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.util.LogUtil
import com.miku.ray.util.requestSubscriptionImportName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

class UrlSchemeActivity : BaseActivity() {
    private val binding by lazy { ActivityLogcatBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        try {
            intent.apply {
                if (action == Intent.ACTION_SEND) {
                    if ("text/plain" == type) {
                        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                            parseUri(it, null)
                        }
                    }
                } else if (action == Intent.ACTION_VIEW) {
                    when (data?.host) {
                        "install-config" -> {
                            val uri: Uri? = intent.data
                            val shareUrl = uri?.getQueryParameter("url").orEmpty()
                            parseUri(shareUrl, uri?.fragment)
                        }

                        "install-sub" -> {
                            val uri: Uri? = intent.data
                            val shareUrl = uri?.getQueryParameter("url").orEmpty()
                            parseUri(shareUrl, uri?.fragment)
                        }

                        else -> {
                            snackbarError(R.string.toast_failure, title = getString(R.string.title_alerter_error))
                        }
                    }
                }
            }

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Error processing URL scheme", e)
        }
    }

    private fun parseUri(uriString: String?, fragment: String?) {
        if (uriString.isNullOrEmpty()) {
            return
        }
        LogUtil.i(AppConfig.TAG, uriString)

        var decodedUrl = URLDecoder.decode(uriString, "UTF-8")
        val uri = Uri.parse(decodedUrl)
        if (uri != null) {
            if (uri.fragment.isNullOrEmpty() && !fragment.isNullOrEmpty()) {
                decodedUrl += "#${fragment}"
            }
            LogUtil.i(AppConfig.TAG, decodedUrl)
            lifecycleScope.launch(Dispatchers.IO) {
                val (count, countSub) = AngConfigManager.importBatchConfig(
                    decodedUrl,
                    "",
                    false
                ) { suggested, existing ->
                    requestSubscriptionImportName(suggested, existing)
                }
                withContext(Dispatchers.Main) {
                    if (count + countSub > 0) {
                        snackbarDefault(R.string.import_subscription_success, title = getString(R.string.title_alerter_info))
                    } else {
                        snackbarDefault(R.string.import_subscription_failure, title = getString(R.string.title_alerter_info))
                    }
                }
            }
        }
    }
}
