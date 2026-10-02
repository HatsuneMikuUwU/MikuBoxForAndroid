package com.miku.ray.service
import com.miku.ray.contracts.IDialerService

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class DialerWebviewService : IDialerService {
    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val keepAliveInterval = 30_000L

    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            webView?.let {
                it.resumeTimers()
                it.onResume()
            }
            handler.postDelayed(this, keepAliveInterval)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun start(context: Context, dialerAddr: String) {
        if (webView != null) stop()
        if (dialerAddr.isEmpty()) return
        val dialerUrl = "http://$dialerAddr/"

        webView = WebView(context.applicationContext).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.onResume()
                    view?.resumeTimers()
                }
            }

            loadUrl(dialerUrl)
        }

        handler.post(keepAliveRunnable)
    }

    override fun stop() {
        handler.removeCallbacks(keepAliveRunnable)
        webView?.apply {
            stopLoading()
            pauseTimers()
            onPause()
            destroy()
        }
        webView = null
    }

}
