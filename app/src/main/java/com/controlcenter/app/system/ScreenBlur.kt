package com.controlcenter.app.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Emulacja rozmycia tla dla Androida 8-11 (systemowy blur jest dopiero od 12).
 *
 * Pomysl: tuz przed pokazaniem panelu robimy zmniejszony zrzut ekranu przez MediaProjection,
 * rozmywamy go programowo i rysujemy jako tlo panelu.
 * Zgode na przechwytywanie ekranu uzytkownik daje raz (po restarcie telefonu trzeba ja dac ponownie).
 */
object ScreenBlur {

    /** Ustawiane przez ScreenCaptureService. */
    @Volatile
    var projection: MediaProjection? = null

    /** Zmniejszamy ekran tyle razy - przyspiesza rozmywanie i samo juz wygladza obraz. */
    private const val SCALE = 6

    /**
     * Robi jedna klatke ekranu i zwraca rozmyta bitmape (zmniejszona, trzeba ja rozciagnac na caly ekran).
     * Wywoluje onDone dokladnie raz: z bitmapa albo z null, jesli sie nie udalo (wtedy panel dostaje zwykle przyciemnienie).
     */
    fun capture(ctx: Context, handler: Handler, strength: Int, onDone: (Bitmap?) -> Unit) {
        val mp = projection
        if (mp == null) {
            onDone(null)
            return
        }

        var finished = false
        var display: VirtualDisplay? = null
        var reader: ImageReader? = null

        fun finish(bmp: Bitmap?) {
            if (finished) return
            finished = true
            try {
                display?.release()
            } catch (e: Exception) {
            }
            try {
                reader?.close()
            } catch (e: Exception) {
            }
            onDone(bmp)
        }

        try {
            val wm = ctx.getSystemService(WindowManager::class.java)
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)

            val sw = (dm.widthPixels / SCALE).coerceAtLeast(16)
            val sh = (dm.heightPixels / SCALE).coerceAtLeast(16)

            val r = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2)
            reader = r
            r.setOnImageAvailableListener({ rd ->
                val img = try {
                    rd.acquireLatestImage()
                } catch (e: Exception) {
                    null
                }
                if (img != null) {
                    val bmp = try {
                        imageToBitmap(img, sw, sh)
                    } finally {
                        img.close()
                    }
                    finish(bmp?.let { blur(it, strength) })
                }
            }, handler)

            display = mp.createVirtualDisplay(
                "cc-blur", sw, sh, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                r.surface, null, handler
            )

            // zabezpieczenie: jesli klatka nie przyjdzie, nie blokujemy panelu
            handler.postDelayed({ finish(null) }, 700)
        } catch (e: Exception) {
            finish(null)
        }
    }

    private fun imageToBitmap(img: Image, w: Int, h: Int): Bitmap? {
        val plane = img.planes.firstOrNull() ?: return null
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * w
        val full = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
        full.copyPixelsFromBuffer(plane.buffer)
        return if (full.width == w) full else Bitmap.createBitmap(full, 0, 0, w, h)
    }

    /** Rozmycie: trzy przejscia "box blur" w poziomie i pionie ~ rozmycie Gaussa. */
    fun blur(src: Bitmap, strength: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(px.size)
        val r = (2 + strength * 10 / 150).coerceIn(2, 12)

        repeat(3) {
            pass(px, tmp, h, w, w, 1, r) // poziomo
            pass(tmp, px, w, h, 1, w, r) // pionowo
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Jedno przejscie ruchomej sredniej.
     * lines - ile linii, len - dlugosc linii, lineStride - przesuniecie miedzy liniami, step - przesuniecie w linii.
     */
    private fun pass(
        src: IntArray, dst: IntArray,
        lines: Int, len: Int, lineStride: Int, step: Int, r: Int,
    ) {
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val base = line * lineStride
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[base + i.coerceIn(0, len - 1) * step]
                sr += (p shr 16) and 255
                sg += (p shr 8) and 255
                sb += p and 255
            }
            for (i in 0 until len) {
                dst[base + i * step] =
                    (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[base + minOf(i + r + 1, len - 1) * step]
                val sub = src[base + maxOf(i - r, 0) * step]
                sr += ((add shr 16) and 255) - ((sub shr 16) and 255)
                sg += ((add shr 8) and 255) - ((sub shr 8) and 255)
                sb += (add and 255) - (sub and 255)
            }
        }
    }
}
