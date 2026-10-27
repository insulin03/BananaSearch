package com.banana.search.boot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.banana.search.MainActivity
import com.banana.search.R
import com.banana.search.core.AppLog

class RestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        AppLog.i(TAG, "收到广播 $action")
        notifyNeedAuth(context)
    }

    private fun notifyNeedAuth(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val pi = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_capture)
            .setContentTitle(context.getString(R.string.app_title))
            .setContentText("设备重启后需要重新授权屏幕投屏，点此打开应用")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { nm.notify(NOTIFY_ID, n) }
    }

    private companion object {
        const val TAG = "Restart"
        const val CHANNEL = "qsearch_boot"
        const val NOTIFY_ID = 9001
    }
}
