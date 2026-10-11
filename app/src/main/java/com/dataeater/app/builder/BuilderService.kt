package com.dataeater.app.builder

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder

/** Keeps local, user-started preparation alive while the screen is in the background. */
class BuilderService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = "database-preparation"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel, "Database preparation", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, com.dataeater.app.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, channel).setSmallIcon(com.dataeater.app.R.mipmap.ic_launcher)
            .setContentTitle("Creating database").setContentText("Local preparation is running in the background")
            .setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(9201, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(9201, notification)
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelOperation?.invoke()
        stopSelf()
    }
    companion object {
        @Volatile var cancelOperation: (() -> Unit)? = null
    }
}
