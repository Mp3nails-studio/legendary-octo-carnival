package com.controlcenter.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.controlcenter.app.MainActivity
import com.controlcenter.app.system.MediaInfo
import com.controlcenter.app.system.MediaState
import com.controlcenter.app.system.NotifHub
import com.controlcenter.app.system.NotifItem
import com.controlcenter.app.system.SystemControls

/** Stan panelu + akcje. Odswiezany co chwile, gdy panel jest otwarty. */
class PanelController(
    private val ctx: Context,
    private val closePanel: () -> Unit,
) {
    var wifi by mutableStateOf(false)
    var bluetooth by mutableStateOf(false)
    var mobileData by mutableStateOf(false)
    var airplane by mutableStateOf(false)
    var torch by mutableStateOf(false)
    var rotationLocked by mutableStateOf(false)
    var silent by mutableStateOf(false)
    var dnd by mutableStateOf(false)
    var musicMuted by mutableStateOf(false)
    var brightness by mutableFloatStateOf(0.5f)
    var volume by mutableFloatStateOf(0.5f)
    var media by mutableStateOf<MediaInfo?>(null)

    var notifAccess by mutableStateOf(false)
    var notifs by mutableStateOf<List<NotifItem>>(emptyList())

    private var draggingBrightness = false
    private var draggingVolume = false
    private var askedForWrite = false

    fun refresh() {
        wifi = SystemControls.isWifiOn(ctx)
        bluetooth = SystemControls.isBluetoothOn(ctx)
        mobileData = SystemControls.isMobileDataActive(ctx)
        airplane = SystemControls.isAirplane(ctx)
        torch = SystemControls.torchOn
        rotationLocked = SystemControls.isRotationLocked(ctx)
        silent = SystemControls.isRingerSilent(ctx)
        dnd = SystemControls.isDnd(ctx)
        musicMuted = SystemControls.isMusicMuted(ctx)
        if (!draggingBrightness) brightness = SystemControls.getBrightness(ctx)
        if (!draggingVolume) volume = SystemControls.getVolume(ctx)
        media = MediaState.current(ctx)
        refreshNotifs()
    }

    fun refreshNotifs() {
        notifAccess = NotifHub.hasAccess()
        val list = NotifHub.snapshot(ctx)
        if (list != notifs) notifs = list // bez zbednej rekompozycji, gdy nic sie nie zmienilo
    }

    private fun after(openedExternalScreen: Boolean) {
        if (openedExternalScreen) closePanel()
    }

    // ---- przelaczniki ----
    fun toggleWifi() = after(SystemControls.toggleWifi(ctx))
    fun toggleBluetooth() = after(SystemControls.toggleBluetooth(ctx))
    fun toggleMobileData() = after(SystemControls.toggleMobileData(ctx))
    fun toggleAirplane() = after(SystemControls.toggleAirplane(ctx))
    fun toggleRotation() = after(SystemControls.toggleRotation(ctx))
    fun toggleDnd() = after(SystemControls.toggleDnd(ctx))

    fun toggleTorch() {
        torch = !torch // od razu pokazujemy zmiane, prawdziwy stan przyjdzie z callbacku
        SystemControls.toggleTorch(ctx)
    }

    fun toggleRinger() {
        SystemControls.toggleRinger(ctx)
        silent = SystemControls.isRingerSilent(ctx)
    }

    fun toggleMusicMute() {
        SystemControls.toggleMusicMute(ctx)
        musicMuted = SystemControls.isMusicMuted(ctx)
    }

    // ---- skroty ----
    fun openCamera() = after(SystemControls.openCamera(ctx))
    fun openAlarms() = after(SystemControls.openAlarms(ctx))
    fun openHotspot() = after(SystemControls.openHotspot(ctx))
    fun openSystemSettings() = after(SystemControls.openSystemSettings(ctx))

    fun openApp() {
        SystemControls.launch(ctx, Intent(ctx, MainActivity::class.java))
        closePanel()
    }

    // ---- suwaki ----
    fun onBrightness(v: Float) {
        brightness = v
        if (SystemControls.canWrite(ctx)) {
            SystemControls.setBrightness(ctx, v)
        } else if (!askedForWrite) {
            askedForWrite = true
            SystemControls.requestWritePermission(ctx)
            closePanel()
        }
    }

    fun brightnessDragging(d: Boolean) {
        draggingBrightness = d
    }

    fun onVolume(v: Float) {
        volume = v
        SystemControls.setVolume(ctx, v)
    }

    fun volumeDragging(d: Boolean) {
        draggingVolume = d
    }

    // ---- muzyka ----
    fun mediaPrev() {
        media?.controller?.transportControls?.skipToPrevious()
    }

    fun mediaNext() {
        media?.controller?.transportControls?.skipToNext()
    }

    fun mediaToggle() {
        val m = media ?: return
        val tc = m.controller?.transportControls ?: return
        if (m.playing) tc.pause() else tc.play()
    }

    // ---- powiadomienia ----
    fun openNotif(item: NotifItem) {
        try {
            item.intent?.send()
        } catch (e: Exception) {
            // powiadomienie moglo stracic wazność
        }
        closePanel()
    }

    fun dismissNotif(item: NotifItem) {
        NotifHub.dismiss(item.key)
        notifs = notifs.filterNot { it.key == item.key } // znika od razu, bez czekania na system
    }

    fun clearAllNotifs() {
        NotifHub.dismissAll()
        notifs = notifs.filterNot { it.clearable }
    }

    fun openNotifAccessSettings() {
        SystemControls.launch(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        closePanel()
    }
}
