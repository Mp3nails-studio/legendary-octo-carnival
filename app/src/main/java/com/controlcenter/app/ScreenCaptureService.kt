package com.controlcenter.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.controlcenter.app.system.ScreenBlur

/**
 * Trzyma "zgode na przechwytywanie ekranu" (MediaProjection) potrzebna do emulacji blura
 * na Androidzie 8-11. Sam nic nie nagrywa - zrzut jest robiony tylko na moment otwarcia panelu.
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val CHANNEL_ID = "cc_capture"
        private const val NOTIF_ID = 43
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        const val ACTION_STOP = "com.controlcenter.app.STOP_CAPTURE"

        fun start(c: Context, resultCode: Int, data: Intent) {
            val i = Intent(c, ScreenCaptureService::class.java)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(c, i)
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, ScreenCaptureService::class.java))
            ScreenBlur.projection = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // zgoda wazna tylko dla tego procesu - po restarcie systemu trzeba dac ja od nowa
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()

        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val mp = mpm.getMediaProjection(code, data)
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    ScreenBlur.projection = null
                    stopSelf()
                }
            }, null)
            ScreenBlur.projection = mp
        } catch (e: Exception) {
            ScreenBlur.projection = null
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ScreenBlur.projection?.let { runCatching { it.stop() } }
        ScreenBlur.projection = null
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Rozmycie tla", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("Rozmycie tla jest aktywne")
            .setContentText("Zrzut ekranu jest robiony tylko przy otwarciu panelu")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }
}
