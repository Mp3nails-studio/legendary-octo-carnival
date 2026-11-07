package com.controlcenter.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Po restarcie telefonu uruchamia panel, jesli byl wlaczony. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs.enabled(context) && Settings.canDrawOverlays(context)) {
            runCatching { OverlayService.start(context) }
        }
    }
}
