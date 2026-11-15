package com.choi.nawa.sdkcheck

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Every probe result goes both to the on-screen log and to logcat (tag NAWA_SDK). */
object ProbeLog {
    const val TAG = "NAWA_SDK"

    /** Newest first, for the screen */
    val lines = mutableStateListOf<String>()

    private val main = Handler(Looper.getMainLooper())
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(section: String, message: String) {
        val line = "${clock.format(Date())} [$section] $message"
        Log.i(TAG, line)
        // SDK callbacks arrive on binder threads; Compose state is touched on the main thread only
        main.post {
            lines.add(0, line)
            if (lines.size > 500) lines.removeAt(lines.lastIndex)
        }
    }

    fun clear() = main.post { lines.clear() }
}

/** Runs [block] and logs any exception instead of crashing, so one broken API doesn't stop the rest. */
inline fun probe(section: String, what: String, block: () -> Unit) {
    try {
        block()
    } catch (t: Throwable) {
        ProbeLog.log(section, "$what threw ${t.javaClass.name}: ${t.message}")
    }
}
