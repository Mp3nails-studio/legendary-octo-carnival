package com.controlcenter.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.controlcenter.app.system.ScreenBlur
import com.controlcenter.app.ui.glass
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Bg = Color(0xFF14161B)
private val Accent = Color(0xFF8A93A6)

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // zgoda na przechwytywanie ekranu (potrzebna tylko do rozmycia na Androidzie 8-11)
    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val data = r.data
            if (r.resultCode == RESULT_OK && data != null) {
                ScreenCaptureService.start(this, r.resultCode, data)
            }
        }

    private fun requestBlurCapture() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Bg)) {
                SetupScreen(this, onEnableBlurCapture = { requestBlurCapture() })
            }
        }
    }
}

@Composable
private fun SetupScreen(ctx: Context, onEnableBlurCapture: () -> Unit) {
    // licznik rosnie przy kazdym powrocie na ekran i co chwile, zeby odswiezyc stan uprawnien
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(800)
            tick++
        }
    }

    val refreshKey = tick // czytanie stanu wymusza ponowne obliczenie uprawnien
    val overlayOk = Settings.canDrawOverlays(ctx)
    val writeOk = Settings.System.canWrite(ctx)
    val listenerOk = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    val dndOk = ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    val captureOk = ScreenBlur.projection != null

    var enabled by remember { mutableStateOf(Prefs.enabled(ctx)) }
    var trigW by remember { mutableIntStateOf(Prefs.triggerWidth(ctx)) }
    var trigH by remember { mutableIntStateOf(Prefs.triggerHeight(ctx)) }
    var trigOff by remember { mutableIntStateOf(Prefs.triggerOffset(ctx)) }
    var blur by remember { mutableIntStateOf(Prefs.blur(ctx)) }
    var showTrig by remember { mutableStateOf(Prefs.showTrigger(ctx)) }

    fun restartIfRunning() {
        if (enabled && overlayOk) OverlayService.start(ctx)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Control Center", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Przesun palcem w dol przy gornej krawedzi ekranu. Lewa strona otwiera powiadomienia " +
                "z zegarem, prawa strona szybkie ustawienia.",
            color = Color.White.copy(alpha = 0.7f),
        )

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wlacz panel", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(
                        if (overlayOk) "Panel dziala nad wszystkimi aplikacjami"
                        else "Najpierw nadaj uprawnienie ponizej",
                        color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = enabled && overlayOk,
                    onCheckedChange = { on ->
                        if (on && !overlayOk) {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            )
                        } else {
                            enabled = on
                            Prefs.setEnabled(ctx, on)
                            if (on) OverlayService.start(ctx) else OverlayService.stop(ctx)
                        }
                    },
                )
            }
        }

        Text("Uprawnienia", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        PermRow(
            "Wyswietlanie nad innymi aplikacjami", "Wymagane - bez tego panel sie nie pokaze",
            overlayOk,
        ) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }
        PermRow("Zmiana ustawien systemu", "Jasnosc ekranu i blokada obrotu", writeOk) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            )
        }
        PermRow(
            "Dostep do powiadomien",
            "Lista powiadomien w lewym panelu i sterowanie muzyka",
            listenerOk,
        ) {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        PermRow("Nie przeszkadzac", "Przelacznik trybu Nie przeszkadzac", dndOk) {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Text("Rozmycie tla (Android 8-11)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Card {
                Text(
                    "Ten Android nie ma systemowego blura, wiec aplikacja robi zrzut tego, co jest pod panelem, " +
                        "rozmywa go i uzywa jako tla. System poprosi o zgode na przechwytywanie ekranu. " +
                        "Zrzut powstaje tylko w chwili otwarcia panelu, nic nie jest zapisywane ani wysylane. " +
                        "Po restarcie telefonu zgode trzeba dac ponownie.",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (captureOk) "Rozmycie aktywne" else "Rozmycie wylaczone",
                        color = if (captureOk) Color(0xFF58E08A) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (captureOk) ScreenCaptureService.stop(ctx) else onEnableBlurCapture()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) { Text(if (captureOk) "Wylacz" else "Wlacz rozmycie") }
                }
            }
        }

        Text("Wyglad i gest", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LabeledSlider("Szerokosc kazdej strefy gestu: $trigW%", trigW.toFloat(), 15f, 50f) {
                    trigW = it.roundToInt()
                }
                LabeledSlider("Wysokosc strefy: ${trigH}dp", trigH.toFloat(), 12f, 80f) {
                    trigH = it.roundToInt()
                }
                LabeledSlider("Odstep od gory: ${trigOff}dp", trigOff.toFloat(), 0f, 80f) {
                    trigOff = it.roundToInt()
                }
                LabeledSlider("Sila rozmycia tla: $blur", blur.toFloat(), 0f, 150f) {
                    blur = it.roundToInt()
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pokaz strefy gestu (czerwone paski)", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = showTrig, onCheckedChange = { showTrig = it })
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        Prefs.setTriggerWidth(ctx, trigW)
                        Prefs.setTriggerHeight(ctx, trigH)
                        Prefs.setTriggerOffset(ctx, trigOff)
                        Prefs.setBlur(ctx, blur)
                        Prefs.setShowTrigger(ctx, showTrig)
                        restartIfRunning()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("Zapisz ustawienia") }
            }
        }

        Text(
            "Wskazowka: Android nie pozwala zwyklym aplikacjom przelaczac Wi-Fi, Bluetooth, " +
                "trybu samolotowego ani danych komorkowych jednym dotknieciem. Te przyciski " +
                "pokazuja stan i otwieraja systemowy panel, w ktorym zmienisz ustawienie.",
            color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .glass(shape)
            .padding(16.dp)
    ) { content() }
}

@Composable
private fun PermRow(title: String, sub: String, granted: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .glass(shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (granted) Color(0xFF58E08A) else Color.White.copy(alpha = 0.6f),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
    Slider(value = value, onValueChange = onChange, valueRange = min..max)
}
