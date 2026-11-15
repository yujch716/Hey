package com.choi.nawa.sdkcheck

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

const val SERVER_URL = "http://10.0.2.2:3000"
const val WEB_URL = "http://10.0.2.2:5173"

class Probes(
    val navi: NaviProbe,
    val location: LocationProbe,
    val vehicle: VehicleProbe,
    val tts: TtsProbe,
)

/** One screen, one button per API. Results go to the log on the right and to logcat (NAWA_SDK). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SdkCheckScreen(probes: Probes, onRequestPermissions: () -> Unit) {
    var showWeb by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("SDK 검증", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Section("0. 권한")
            Buttons {
                Btn("위치 권한 요청 + 상태") { onRequestPermissions() }
                Btn("로그 지우기", primary = false) { ProbeLog.clear() }
            }

            Section("1. NaviHelper")
            Buttons {
                Btn("initialize") { probes.navi.initialize() }
                Btn("requestRoute 강남역") { probes.navi.requestRoute(GANGNAM_STATION) }
                Btn("requestRoute 역삼역") { probes.navi.requestRoute(YEOKSAM_STATION) }
                Btn("cancelRoute") { probes.navi.cancelRoute() }
                Btn("getRouteStateInfo", primary = false) { probes.navi.getRouteStateInfo() }
                Btn("getDestinationInfo", primary = false) { probes.navi.getDestinationInfo() }
                Btn("getCurrentLocationInfo", primary = false) { probes.navi.getCurrentLocationInfo() }
                Btn("getTBTInfo", primary = false) { probes.navi.getTbtInfo() }
                Btn("requestTrip 역삼역", primary = false) { probes.navi.requestTrip(YEOKSAM_STATION) }
            }

            Section("2. 위치")
            Buttons {
                Btn("Fused isSupported") { probes.location.checkFused() }
                Btn("Fused lastKnown", primary = false) { probes.location.fusedLastKnown() }
                Btn("Fused 구독 시작", primary = false) { probes.location.fusedWatch(true) }
                Btn("Fused 구독 중지", primary = false) { probes.location.fusedWatch(false) }
                Btn("LocationManager 상태") { probes.location.locationManagerSnapshot() }
                Btn("LM 구독 시작", primary = false) { probes.location.locationManagerWatch(true) }
                Btn("LM 구독 중지", primary = false) { probes.location.locationManagerWatch(false) }
            }

            Section("3. Vehicle")
            Buttons {
                Btn("initialize") { probes.vehicle.initialize() }
                Btn("capabilities") { probes.vehicle.capabilities() }
                Btn("문 상태 읽기", primary = false) { probes.vehicle.readDoors() }
                Btn("문 콜백 등록", primary = false) { probes.vehicle.watchDoors(true) }
                Btn("기어·브레이크 읽기", primary = false) { probes.vehicle.readDriveState() }
                Btn("기어·브레이크 콜백 등록", primary = false) { probes.vehicle.watchDriveState(true) }
                Btn("CarInfo", primary = false) { probes.vehicle.readCarInfo() }
            }

            Section("4. TextToSpeech")
            Buttons {
                Btn("initialize (ON_DEVICE)") { probes.tts.initialize() }
                Btn("\"나왔어요\"", primary = false) { probes.tts.speak("나왔어요") }
                Btn("\"2번 정류지 손님이 5분 늦어요\"", primary = false) { probes.tts.speak("2번 정류지 손님이 5분 늦어요") }
            }

            Section("5. 네트워크 / 6. WebView")
            Buttons {
                Btn("서버 $SERVER_URL") { NetProbe.get("$SERVER_URL/share/health-check") }
                Btn("웹 $WEB_URL", primary = false) { NetProbe.get("$WEB_URL/") }
                Btn(if (showWeb) "WebView 닫기" else "WebView 열기") { showWeb = !showWeb }
            }
        }

        Column(
            Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .background(Color(0xFF111418)),
        ) {
            if (showWeb) {
                ProbeWebView(
                    url = "$WEB_URL/?state=near&freeze=1",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp),
            ) {
                items(ProbeLog.lines) { line ->
                    Text(line, color = Color(0xFFD8DEE6), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) =
    Text(title, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Buttons(content: @Composable () -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }

@Composable
private fun Btn(label: String, primary: Boolean = true, onClick: () -> Unit) =
    if (primary) Button(onClick = onClick) { Text(label) } else OutlinedButton(onClick = onClick) { Text(label) }

/** The driver map could be the passenger web page. Does the head unit even have a WebView? */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ProbeWebView(url: String, modifier: Modifier) {
    val section = "WEBVIEW"
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            try {
                val pkg = if (Build.VERSION.SDK_INT >= 26) WebView.getCurrentWebViewPackage() else null
                ProbeLog.log(section, "provider=${pkg?.packageName} ${pkg?.versionName}")
                WebView(ctx).apply {
                    // AndroidView defaults to WRAP_CONTENT, and a wrap_content WebView resolves vh/dvh to 0,
                    // which collapses the page's h-[100dvh] frame to nothing
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = ProbeLog.log(section, "pageStarted $url")
                        override fun onPageFinished(view: WebView, url: String) {
                            ProbeLog.log(section, "pageFinished $url title=\"${view.title}\" view=${view.width}x${view.height}")
                            // What actually rendered: viewport size and the first visible text
                            view.postDelayed({
                                view.evaluateJavascript(
                                    "(() => { const f = document.getElementById('root')?.firstElementChild; " +
                                        "return JSON.stringify({w: innerWidth, h: innerHeight, dpr: devicePixelRatio, " +
                                        "body: document.body.scrollHeight, frame: f && f.getBoundingClientRect().height, " +
                                        "frameCss: f && getComputedStyle(f).height, dvh: CSS.supports('height', '100dvh'), " +
                                        "ua: navigator.userAgent.slice(0, 120), text: document.body.innerText.slice(0, 80)}) })()",
                                ) { ProbeLog.log(section, "dom $it") }
                            }, 3000)
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) =
                            ProbeLog.log(section, "error ${error.errorCode} ${error.description} ${request.url} mainFrame=${request.isForMainFrame}")
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                            ProbeLog.log(section, "console ${m.messageLevel()} ${m.message().take(160)}")
                            return true
                        }
                    }
                    ProbeLog.log(section, "loadUrl $url")
                    loadUrl(url)
                }
            } catch (t: Throwable) {
                // e.g. MissingWebViewPackageException when the image ships without a WebView provider
                ProbeLog.log(section, "WebView unavailable: ${t.javaClass.name}: ${t.message}")
                TextView(ctx).apply { text = "WebView를 만들 수 없어요: ${t.javaClass.simpleName}" }
            }
        },
    )
}
