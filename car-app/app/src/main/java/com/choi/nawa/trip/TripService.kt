package com.choi.nawa.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.choi.nawa.MainActivity
import com.choi.nawa.R
import com.choi.nawa.container
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

/**
 * Keeps the trip loop (location + ETA every 3 s, driver WebSocket) alive while the navi app
 * has the screen: requestRoute moves our activity to the background.
 */
class TripService : LifecycleService() {
    private var loop: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        val controller = container.trip.value
        if (controller == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop == null) {
            val replies = lifecycleScope.launch { controller.replyEvents.collect { showReply(it) } }
            loop = lifecycleScope.launch {
                controller.runLoop()
                replies.cancel()
                ServiceCompat.stopForeground(this@TripService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * A passenger message: bring the trip panel to the front. If the OS blocks starting an
     * activity from the background, fall back to a heads-up notification that opens it.
     */
    private suspend fun showReply(reply: ReplyLog) {
        val started = runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        // Coming to the front can take a moment on the head unit; give it up to FRONT_CHECK_MS
        withTimeoutOrNull(FRONT_CHECK_MS) { while (!MainActivity.isResumed) delay(100) }
        if (MainActivity.isResumed) {
            Log.i(TAG, "reply ${reply.stopOrder}/${reply.type}: app brought to front")
            return
        }
        Log.i(TAG, "reply ${reply.stopOrder}/${reply.type}: app not in front (start=${started.exceptionOrNull() ?: "ok"}), heads-up notification")
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(REPLY_CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(REPLY_CHANNEL_ID, "탑승자 메시지", NotificationManager.IMPORTANCE_HIGH))
        }
        nm.notify(
            REPLY_NOTIFICATION_ID,
            NotificationCompat.Builder(this, REPLY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("탑승자 메시지")
                .setContentText(reply.text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(openPanel())
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun openPanel() = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "운행 중", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("나와 운행 중")
            .setContentText("탑승자에게 차 위치를 보내고 있어요")
            .setContentIntent(openPanel())
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "trip"
        const val NOTIFICATION_ID = 1
        const val REPLY_CHANNEL_ID = "replies"
        const val REPLY_NOTIFICATION_ID = 2
        const val FRONT_CHECK_MS = 3_000L
        const val TAG = "NAWA_PORT"
    }
}
