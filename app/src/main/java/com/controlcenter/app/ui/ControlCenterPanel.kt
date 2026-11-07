package com.controlcenter.app.ui

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controlcenter.app.system.NotifItem
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ktory panel pokazujemy: prawa strona = szybkie ustawienia, lewa = zegar i powiadomienia. */
enum class PanelMode { QUICK, NOTIFICATIONS }

/**
 * Caly panel. Wyjezdza z gory. Tlo: systemowy blur (Android 12+) albo rozmyty zrzut ekranu [backdrop]
 * (Android 8-11), a na nim lekkie przyciemnienie.
 */
@Composable
fun ControlCenterPanel(
    c: PanelController,
    mode: PanelMode,
    closeRequested: Boolean,
    blurActive: Boolean,
    backdrop: Bitmap?,
    topInsetPx: Int,
    onRequestClose: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(closeRequested) { if (closeRequested) shown = false }
    LaunchedEffect(Unit) {
        while (true) {
            c.refresh()
            delay(700)
        }
    }

    val scrimTarget = if (blurActive || backdrop != null) 0.22f else 0.55f
    val scrim by animateFloatAsState(if (shown) scrimTarget else 0f, tween(250), label = "scrim")
    val backdropAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(250), label = "backdrop")
    val topInset = with(LocalDensity.current) { topInsetPx.toDp() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onRequestClose() } },
    ) {
        if (backdrop != null) {
            Image(
                bitmap = backdrop.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(backdropAlpha),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
        )

        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(tween(300)) { -it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
        ) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = topInset + 6.dp, bottom = 20.dp)
                    // klikniecie w panel nie zamyka go (tapa lapie tlo)
                    .pointerInput(Unit) { detectTapGestures { } }
                    // przeciagniecie w gore zamyka
                    .pointerInput(Unit) {
                        var sum = 0f
                        detectVerticalDragGestures(
                            onDragStart = { sum = 0f },
                            onDragEnd = { sum = 0f },
                            onDragCancel = { sum = 0f },
                        ) { change, amount ->
                            change.consume()
                            sum += amount
                            if (sum < -90f) {
                                sum = 0f
                                onRequestClose()
                            }
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (mode) {
                    PanelMode.QUICK -> QuickContent(c)
                    PanelMode.NOTIFICATIONS -> NotificationsContent(c)
                }
            }
        }
    }
}

// =====================================================================================
//  PRAWY PANEL: szybkie ustawienia
// =====================================================================================

@Composable
private fun QuickContent(c: PanelController) {
    // --- gorny rzad: edycja i ustawienia ---
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTap(Icons.Rounded.Edit, onClick = { c.openApp() })
        IconTap(Icons.Rounded.Settings, onClick = { c.openSystemSettings() })
    }

    // --- Wi-Fi i Bluetooth ---
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WidePill(
            icon = if (c.wifi) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
            label = "Wi-Fi",
            active = c.wifi,
            modifier = Modifier.weight(1f),
            onClick = { c.toggleWifi() },
        )
        WidePill(
            icon = if (c.bluetooth) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
            label = "Bluetooth",
            active = c.bluetooth,
            modifier = Modifier.weight(1f),
            onClick = { c.toggleBluetooth() },
        )
    }

    // --- siatka przelacznikow ---
    GlassCard {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundToggle(Icons.Rounded.SignalCellularAlt, c.mobileData) { c.toggleMobileData() }
                RoundToggle(Icons.Rounded.ScreenLockRotation, c.rotationLocked) { c.toggleRotation() }
                RoundToggle(
                    if (c.torch) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    c.torch,
                ) { c.toggleTorch() }
                RoundToggle(Icons.Rounded.AirplanemodeActive, c.airplane) { c.toggleAirplane() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundToggle(Icons.Rounded.CameraAlt, false) { c.openCamera() }
                RoundToggle(Icons.Rounded.NotificationsOff, c.silent) { c.toggleRinger() }
                RoundToggle(Icons.Rounded.Alarm, false) { c.openAlarms() }
                RoundToggle(Icons.Rounded.WifiTethering, false) { c.openHotspot() }
            }
            GrabHandle()
        }
    }

    // --- jasnosc i glosnosc ---
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillSlider(
                    value = c.brightness,
                    icon = Icons.Rounded.BrightnessMedium,
                    modifier = Modifier.weight(1f),
                    onDragging = { c.brightnessDragging(it) },
                    onValueChange = { c.onBrightness(it) },
                )
                RoundToggle(Icons.Rounded.Bedtime, c.dnd) { c.toggleDnd() }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillSlider(
                    value = c.volume,
                    icon = Icons.Rounded.VolumeUp,
                    modifier = Modifier.weight(1f),
                    onDragging = { c.volumeDragging(it) },
                    onValueChange = { c.onVolume(it) },
                )
                RoundToggle(Icons.Rounded.VolumeOff, c.musicMuted) { c.toggleMusicMute() }
            }
        }
    }

    // --- odtwarzacz ---
    val media = c.media
    GlassCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = if (media?.playing == true) IconOn else IconOff,
                modifier = Modifier.padding(end = 10.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = media?.title ?: "Nic nie gra",
                    color = if (media != null) IconOn else IconOff,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (media != null && media.artist.isNotBlank()) {
                    Text(
                        text = media.artist,
                        color = IconOff,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (media != null) {
                IconTap(Icons.Rounded.SkipPrevious, onClick = { c.mediaPrev() })
                IconTap(
                    if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = { c.mediaToggle() },
                )
                IconTap(Icons.Rounded.SkipNext, onClick = { c.mediaNext() })
            }
        }
    }
}

// =====================================================================================
//  LEWY PANEL: zegar, data i powiadomienia
// =====================================================================================

@Composable
private fun ColumnScope.NotificationsContent(c: PanelController) {
    ClockHeader()

    Text(
        "Powiadomienia",
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
    )

    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            !c.notifAccess -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(26.dp))
                        .clickable { c.openNotifAccessSettings() }
                        .padding(18.dp),
                ) {
                    Text(
                        "Wlacz dostep do powiadomien",
                        color = IconOn,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Text(
                        "Dotknij, zeby zezwolic aplikacji na odczyt powiadomien.",
                        color = IconOff,
                        fontSize = 13.sp,
                    )
                }
            }

            c.notifs.isEmpty() -> {
                Text(
                    "Brak nowych powiadomien",
                    color = IconOff,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                )
            }

            else -> {
                c.notifs.forEach { item ->
                    NotifCard(item, onOpen = { c.openNotif(item) }, onDismiss = { c.dismissNotif(item) })
                }
                if (c.notifs.any { it.clearable }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "Wyczysc wszystko",
                            color = IconOn,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .glass(RoundedCornerShape(50))
                                .clickable { c.clearAllNotifs() }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockHeader() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val time = remember(now / 60_000) { DateFormat.getTimeFormat(ctx).format(Date(now)) }
    val date = remember(now / 60_000) {
        SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(now))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(time, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.size(12.dp))
        Text(
            date,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun NotifCard(item: NotifItem, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val time = remember(item.whenMs) { DateFormat.getTimeFormat(ctx).format(Date(item.whenMs)) }

    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp))
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.icon != null) {
                Image(
                    bitmap = item.icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                "${item.app}  $time",
                color = IconOff,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.title.isNotBlank()) {
                Text(
                    item.title,
                    color = IconOn,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.text.isNotBlank()) {
                Text(
                    item.text,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.clearable) {
            IconTap(Icons.Rounded.Close, size = 38.dp, tint = IconOff, onClick = onDismiss)
        }
    }
}
