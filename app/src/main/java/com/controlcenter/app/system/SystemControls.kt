package com.controlcenter.app.system

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Wszystkie operacje na ustawieniach systemu w jednym miejscu.
 *
 * Funkcje toggle* zwracaja true, gdy otworzyly zewnetrzny ekran (ustawienia systemu) -
 * wtedy panel powinien sie zamknac.
 *
 * Od Androida 10 aplikacje nie moga same przelaczac Wi-Fi, Bluetooth, trybu samolotowego
 * ani danych komorkowych - dla nich otwieramy systemowy panel / ekran ustawien.
 */
object SystemControls {

    @Volatile
    var torchOn = false
        private set
    private var torchRegistered = false

    fun init(c: Context) {
        if (torchRegistered) return
        torchRegistered = true
        val cm = c.getSystemService(CameraManager::class.java) ?: return
        cm.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                torchOn = enabled
            }
        }, Handler(Looper.getMainLooper()))
    }

    // ---------- pomocnicze ----------

    private fun audio(c: Context) = c.getSystemService(AudioManager::class.java)

    fun launch(c: Context, intent: Intent): Boolean = try {
        c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Toast.makeText(c, "Nie mozna otworzyc tego ekranu", Toast.LENGTH_SHORT).show()
        false
    }

    // ---------- jasnosc i uprawnienie WRITE_SETTINGS ----------

    fun canWrite(c: Context) = Settings.System.canWrite(c)

    fun requestWritePermission(c: Context) {
        Toast.makeText(c, "Zezwol aplikacji na zmiane ustawien systemu", Toast.LENGTH_LONG).show()
        launch(
            c,
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${c.packageName}"))
        )
    }

    fun getBrightness(c: Context): Float {
        val v = try {
            Settings.System.getInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            128
        }
        return (v / 255f).coerceIn(0f, 1f)
    }

    fun setBrightness(c: Context, f: Float) {
        if (!canWrite(c)) return
        try {
            val cr = c.contentResolver
            Settings.System.putInt(
                cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                cr, Settings.System.SCREEN_BRIGHTNESS,
                max(5, (f.coerceIn(0f, 1f) * 255).roundToInt())
            )
        } catch (e: SecurityException) {
            // brak uprawnienia
        }
    }

    // ---------- glosnosc ----------

    fun getVolume(c: Context): Float {
        val am = audio(c)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    fun setVolume(c: Context, f: Float) {
        val am = audio(c)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (f.coerceIn(0f, 1f) * max).roundToInt(), 0)
        } catch (e: SecurityException) {
            // niektore urzadzenia blokuja zmiane w trybie Nie przeszkadzac
        }
    }

    fun isMusicMuted(c: Context) = audio(c).isStreamMute(AudioManager.STREAM_MUSIC)

    fun toggleMusicMute(c: Context) {
        val am = audio(c)
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (isMusicMuted(c)) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE,
            0
        )
    }

    // ---------- tryb dzwonka ----------

    fun isRingerSilent(c: Context) = audio(c).ringerMode != AudioManager.RINGER_MODE_NORMAL

    fun toggleRinger(c: Context) {
        val am = audio(c)
        try {
            am.ringerMode = if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL)
                AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        } catch (e: SecurityException) {
            Toast.makeText(c, "Brak uprawnien do zmiany trybu dzwonka", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- latarka ----------

    fun toggleTorch(c: Context) {
        val cm = c.getSystemService(CameraManager::class.java) ?: return
        try {
            val id = cm.cameraIdList.firstOrNull {
                val ch = cm.getCameraCharacteristics(it)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: run {
                Toast.makeText(c, "Brak latarki w tym urzadzeniu", Toast.LENGTH_SHORT).show()
                return
            }
            cm.setTorchMode(id, !torchOn)
        } catch (e: Exception) {
            Toast.makeText(c, "Nie mozna wlaczyc latarki", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Wi-Fi / Bluetooth / dane / samolot ----------

    @Suppress("DEPRECATION")
    fun isWifiOn(c: Context): Boolean = try {
        (c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (e: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    fun toggleWifi(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.isWifiEnabled = !wm.isWifiEnabled
            return false
        }
        return launch(c, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
    }

    fun isBluetoothOn(c: Context): Boolean = try {
        c.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    fun toggleBluetooth(c: Context): Boolean =
        launch(c, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    fun isMobileDataActive(c: Context): Boolean = try {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    } catch (e: Exception) {
        false
    }

    fun toggleMobileData(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            launch(c, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        else
            launch(c, Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))

    fun isAirplane(c: Context) =
        Settings.Global.getInt(c.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun toggleAirplane(c: Context): Boolean =
        launch(c, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))

    // ---------- obrot ekranu ----------

    fun isRotationLocked(c: Context) =
        Settings.System.getInt(c.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0

    /** Zwraca true, jesli trzeba bylo otworzyc ekran uprawnien. */
    fun toggleRotation(c: Context): Boolean {
        if (!canWrite(c)) {
            requestWritePermission(c)
            return true
        }
        try {
            Settings.System.putInt(
                c.contentResolver, Settings.System.ACCELEROMETER_ROTATION,
                if (isRotationLocked(c)) 1 else 0
            )
        } catch (e: SecurityException) {
            // brak uprawnienia
        }
        return false
    }

    // ---------- Nie przeszkadzac ----------

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun isDnd(c: Context): Boolean {
        val f = nm(c).currentInterruptionFilter
        return f != NotificationManager.INTERRUPTION_FILTER_ALL &&
            f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    fun toggleDnd(c: Context): Boolean {
        val n = nm(c)
        if (!n.isNotificationPolicyAccessGranted) {
            Toast.makeText(c, "Zezwol na dostep do trybu Nie przeszkadzac", Toast.LENGTH_LONG).show()
            return launch(c, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        n.setInterruptionFilter(
            if (isDnd(c)) NotificationManager.INTERRUPTION_FILTER_ALL
            else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        )
        return false
    }

    // ---------- skroty do aplikacji / ekranow ----------

    fun openCamera(c: Context) = launch(c, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    fun openAlarms(c: Context) = launch(c, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun openHotspot(c: Context): Boolean {
        val tether = Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings")
        return try {
            c.startActivity(tether.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            launch(c, Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    fun openSystemSettings(c: Context) = launch(c, Intent(Settings.ACTION_SETTINGS))
}
