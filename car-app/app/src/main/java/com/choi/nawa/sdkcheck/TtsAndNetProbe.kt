package com.choi.nawa.sdkcheck

import android.content.Context
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

/** TextToSpeech in ON_DEVICE mode: no Client ID/Secret needed. Does it read Korean aloud? */
class TtsProbe(private val context: Context) {
    private val section = "TTS"
    private var tts: TextToSpeech? = null

    private val events = object : EventListener {
        override fun onStart() = log("◀ onStart")
        override fun onReady() = log("◀ onReady")
        override fun onDone() = log("◀ onDone")
        override fun onError(errorType: ErrorType) = log("◀ onError $errorType")
        override fun onStop() = log("◀ onStop")
        override fun onUpdatedRms(rms: Double) = Unit // fires continuously while speaking
    }

    fun initialize(mode: Mode = Mode.ON_DEVICE) = probe(section, "initialize") {
        release()
        val t = TextToSpeech(context.applicationContext, mode)
        tts = t
        log("initialize($mode) …")
        t.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                log("◀ initialize onSuccess")
                t.addEventListener(events)
                t.setLocale(Locale.KOREA)
                log("setLocale(ko-KR)")
            }

            override fun onFailure(errorType: ErrorType) = log("◀ initialize onFailure $errorType")
        })
    }

    fun speak(text: String) = probe(section, "speak") {
        val t = tts ?: return@probe log("not initialized")
        log("speak(\"$text\")")
        t.speak(text, true)
    }

    fun release() = probe(section, "release") {
        tts?.let {
            it.removeEventListener(events)
            it.release()
        }
        tts = null
    }

    private fun log(message: String) = ProbeLog.log(section, message)
}

/** Can the car reach the MacBook's dev servers? 10.0.2.2 is the host's loopback as seen from the emulator. */
object NetProbe {
    private const val SECTION = "NET"

    fun get(url: String) {
        thread(name = "net-probe") {
            val started = System.currentTimeMillis()
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val code = conn.responseCode
                val body = (if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                ProbeLog.log(SECTION, "GET $url → $code in ${System.currentTimeMillis() - started}ms body=${body.take(160).replace('\n', ' ')}")
                conn.disconnect()
            } catch (t: Throwable) {
                ProbeLog.log(SECTION, "GET $url failed: ${t.javaClass.name}: ${t.message}")
            }
        }
    }
}
