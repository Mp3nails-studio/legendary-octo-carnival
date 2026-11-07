package com.controlcenter.app.system

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController as SessionController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap

/**
 * Serwis dostepu do powiadomien. Dzieki niemu:
 *  - czytamy aktywne powiadomienia (lewy panel),
 *  - mozemy je zamykac,
 *  - MediaSessionManager pozwala odczytac grajaca muzyke.
 */
class MediaListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: MediaListener? = null
    }
}

// ---------------------------------------------------------------- powiadomienia

data class NotifItem(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val whenMs: Long,
    val icon: Bitmap?,
    val intent: PendingIntent?,
    val clearable: Boolean,
)

object NotifHub {
    private val iconCache = HashMap<String, Bitmap?>()
    private val nameCache = HashMap<String, String>()

    fun hasAccess(): Boolean = MediaListener.instance != null

    fun snapshot(c: Context): List<NotifItem> {
        val listener = MediaListener.instance ?: return emptyList()
        val arr: Array<StatusBarNotification>? = try {
            listener.activeNotifications
        } catch (e: Exception) {
            null
        }
        if (arr == null) return emptyList()

        return arr.asSequence()
            .filter { it.packageName != c.packageName }
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .filter { it.notification.category != Notification.CATEGORY_TRANSPORT }
            .filter { !it.isOngoing }
            .sortedByDescending { it.postTime }
            .map { sbn ->
                val n = sbn.notification
                val extras = n.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
                NotifItem(
                    key = sbn.key,
                    pkg = sbn.packageName,
                    app = appName(c, sbn.packageName),
                    title = title,
                    text = text,
                    whenMs = sbn.postTime,
                    icon = appIcon(c, sbn.packageName),
                    intent = n.contentIntent,
                    clearable = sbn.isClearable,
                )
            }
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .take(30)
            .toList()
    }

    fun dismiss(key: String) {
        try {
            MediaListener.instance?.cancelNotification(key)
        } catch (e: Exception) {
        }
    }

    fun dismissAll() {
        try {
            MediaListener.instance?.cancelAllNotifications()
        } catch (e: Exception) {
        }
    }

    private fun appName(c: Context, pkg: String): String = nameCache.getOrPut(pkg) {
        try {
            val pm = c.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun appIcon(c: Context, pkg: String): Bitmap? {
        if (iconCache.containsKey(pkg)) return iconCache[pkg]
        val bmp = try {
            c.packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
        } catch (e: Exception) {
            null
        }
        iconCache[pkg] = bmp
        return bmp
    }
}

// ---------------------------------------------------------------- muzyka

/** Informacje o grajacym utworze. Kontroler jest poza konstruktorem, zeby nie psul equals(). */
data class MediaInfo(val title: String, val artist: String, val playing: Boolean) {
    var controller: SessionController? = null
}

object MediaState {
    fun current(c: Context): MediaInfo? {
        return try {
            val msm = c.getSystemService(MediaSessionManager::class.java) ?: return null
            val sessions = msm.getActiveSessions(ComponentName(c, MediaListener::class.java))
            val s = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull()
                ?: return null
            val md = s.metadata
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: "Nieznany utwor"
            val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: ""
            MediaInfo(title, artist, s.playbackState?.state == PlaybackState.STATE_PLAYING)
                .also { it.controller = s }
        } catch (e: SecurityException) {
            null // brak dostepu do powiadomien
        } catch (e: Exception) {
            null
        }
    }
}
