package com.choi.nawa.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import com.choi.nawa.BuildConfig
import com.choi.nawa.ui.theme.Palette

/** The web's driver map for this trip. The token rides in the hash so it never reaches a server log. */
fun driverMapUrl(tripId: String, driverToken: String) = "${BuildConfig.WEB_BASE_URL}/d/$tripId#token=$driverToken"

/**
 * The trip panel's map: the web page /d/:tripId in a WebView. Read-only.
 * MATCH_PARENT is required: a wrap_content WebView resolves the page's 100dvh to 0 (docs/sdk-findings.md).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DriverMapView(url: String, modifier: Modifier = Modifier) {
    var failed by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier.clip(CardShape).background(Palette.Ground)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // chrome://inspect in debug builds, to see the map page's console and network
                if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            failed = false
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) failed = true
                        }

                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (request.isForMainFrame && response.statusCode >= 400) failed = true
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
        )

        if (failed) {
            CenteredMessage("지도를 불러오지 못했어요") {
                SecondaryButton("다시 시도") {
                    failed = false
                    webView?.loadUrl(url)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }
}
