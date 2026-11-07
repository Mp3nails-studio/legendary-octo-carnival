package com.controlcenter.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.controlcenter.app.system.ScreenBlur
import com.controlcenter.app.system.SystemControls
import com.controlcenter.app.ui.ControlCenterPanel
import com.controlcenter.app.ui.PanelController
import com.controlcenter.app.ui.PanelMode

/**
 * Serwis pierwszoplanowy trzymajacy okna systemowe (TYPE_APPLICATION_OVERLAY):
 *  1. dwie cienkie niewidoczne strefy przy gornej krawedzi - lapia przesuniecie palcem w dol
 *     (lewa -> powiadomienia i zegar, prawa -> szybkie ustawienia),
 *  2. pelnoekranowy panel Compose z rozmyciem tla - pokazywany na zadanie.
 */
class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "cc_overlay"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.controlcenter.app.STOP"

        fun start(c: Context) {
            ContextCompat.startForegroundService(c, Intent(c, OverlayService::class.java))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, OverlayService::class.java))
        }
    }

    /** Wlasciciel cyklu zycia dla ComposeView poza Activity. */
    private class ViewOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)
        override val viewModelStore = ViewModelStore()
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private val triggerViews = mutableListOf<View>()
    private var panelView: ComposeView? = null
    private var panelOwner: ViewOwner? = null
    private var capturing = false
    private var closeRequested by mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        SystemControls.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // wywolywane tez po zmianie ustawien - odswiezamy strefy wyzwalania
        addTriggers()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // po obrocie ekranu szerokosc stref trzeba policzyc od nowa
        if (triggerViews.isNotEmpty()) {
            removePanel()
            addTriggers()
        }
    }

    override fun onDestroy() {
        removePanel()
        removeTriggers()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ powiadomienie

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Control Center", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Control Center dziala")
            .setContentText("Lewa strona: powiadomienia, prawa: szybkie ustawienia")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Wylacz", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    // ------------------------------------------------------------------ strefy wyzwalania

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun addTriggers() {
        removeTriggers()
        val screenW = resources.displayMetrics.widthPixels
        val width = (screenW * Prefs.triggerWidth(this) / 100f).toInt().coerceAtLeast(dp(60))
        addTrigger(Gravity.TOP or Gravity.START, width, PanelMode.NOTIFICATIONS)
        addTrigger(Gravity.TOP or Gravity.END, width, PanelMode.QUICK)
    }

    private fun addTrigger(gravity: Int, width: Int, mode: PanelMode) {
        val threshold = dp(36)
        val v = View(this)
        v.setBackgroundColor(
            if (Prefs.showTrigger(this)) Color.argb(140, 255, 60, 60) else Color.TRANSPARENT
        )
        var startY = 0f
        var fired = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY
                    fired = false
                }
                MotionEvent.ACTION_MOVE -> if (!fired && e.rawY - startY > threshold) {
                    fired = true
                    showPanel(mode)
                }
            }
            true
        }

        val lp = WindowManager.LayoutParams(
            width,
            dp(Prefs.triggerHeight(this)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            y = dp(Prefs.triggerOffset(this@OverlayService))
        }

        try {
            wm.addView(v, lp)
            triggerViews.add(v)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun removeTriggers() {
        triggerViews.forEach { v -> runCatching { wm.removeView(v) } }
        triggerViews.clear()
    }

    // ------------------------------------------------------------------ panel

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun showPanel(mode: PanelMode) {
        if (panelView != null || capturing) return

        // Android 8-11: robimy zrzut ekranu i rozmywamy go sami (zanim pojawi sie panel)
        val needsFakeBlur = Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            ScreenBlur.projection != null && Prefs.blur(this) > 0
        if (needsFakeBlur) {
            capturing = true
            ScreenBlur.capture(this, handler, Prefs.blur(this)) { bmp ->
                capturing = false
                if (panelView == null) addPanel(mode, bmp)
            }
        } else {
            addPanel(mode, null)
        }
    }

    private fun addPanel(mode: PanelMode, backdrop: Bitmap?) {
        closeRequested = false

        val blurRadius = Prefs.blur(this)
        val nativeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            wm.isCrossWindowBlurEnabled && blurRadius > 0

        val owner = ViewOwner().also { it.start() }
        val controller = PanelController(this) { hidePanel() }
        val topInset = statusBarHeight()

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                ControlCenterPanel(
                    c = controller,
                    mode = mode,
                    closeRequested = closeRequested,
                    blurActive = nativeBlur,
                    backdrop = backdrop,
                    topInsetPx = topInset,
                    onRequestClose = { hidePanel() },
                )
            }
        }

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (nativeBlur) flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (nativeBlur) blurBehindRadius = dp(blurRadius)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            wm.addView(view, lp)
            panelView = view
            panelOwner = owner
        } catch (e: Exception) {
            owner.destroy()
        }
    }

    private fun hidePanel() {
        if (panelView == null || closeRequested) return
        closeRequested = true // Compose odgrywa animacje wyjscia
        handler.postDelayed({ removePanel() }, 320)
    }

    private fun removePanel() {
        panelView?.let { runCatching { wm.removeView(it) } }
        panelView = null
        panelOwner?.destroy()
        panelOwner = null
        closeRequested = false
    }
}
