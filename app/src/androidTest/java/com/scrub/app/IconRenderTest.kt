package com.scrub.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Temporary: renders the launcher icon under common launcher masks into one PNG sheet. */
@RunWith(AndroidJUnit4::class)
class IconRenderTest {

    @Test
    fun renderMasks() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val icon = ctx.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val size = 384
        val gap = 32
        val masks = listOf<(Float) -> Path>(::circle, ::squircle, ::roundedSquare)
        // Row 1: full color, rows 2-3: themed (monochrome) on light and dark
        val sheet = Bitmap.createBitmap(gap + (size + gap) * masks.size, gap + (size + gap) * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(200, 200, 200))

        masks.forEachIndexed { i, mask ->
            for (row in 0 until 3) {
                val tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val c = Canvas(tile)
                c.clipPath(mask(size.toFloat()))
                // Layers are 108dp; the visible area is the middle 72dp, so draw them 1.5x.
                val full = (size * 1.5f).toInt()
                val off = (size - full) / 2
                when (row) {
                    0 -> {
                        icon.background.setBounds(off, off, off + full, off + full); icon.background.draw(c)
                        icon.foreground.setBounds(off, off, off + full, off + full); icon.foreground.draw(c)
                    }
                    else -> {
                        val (bg, fg) = if (row == 1) Color.rgb(232, 236, 246) to Color.rgb(40, 56, 96)
                        else Color.rgb(32, 38, 52) to Color.rgb(196, 210, 240)
                        c.drawColor(bg)
                        val mono = icon.monochrome!!.mutate()
                        mono.colorFilter = PorterDuffColorFilter(fg, PorterDuff.Mode.SRC_IN)
                        mono.setBounds(off, off, off + full, off + full)
                        mono.draw(c)
                    }
                }
                canvas.drawBitmap(tile, (gap + i * (size + gap)).toFloat(), (gap + row * (size + gap)).toFloat(), Paint())
            }
        }
        val out = File(ctx.getExternalFilesDir(null), "icon_masks.png")
        out.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun circle(s: Float) = Path().apply { addCircle(s / 2, s / 2, s / 2, Path.Direction.CW) }

    private fun roundedSquare(s: Float) = Path().apply {
        addRoundRect(RectF(0f, 0f, s, s), s * 0.22f, s * 0.22f, Path.Direction.CW)
    }

    /** Superellipse |x|^4 + |y|^4 = 1. */
    private fun squircle(s: Float) = Path().apply {
        val r = s / 2
        for (i in 0..360) {
            val t = Math.toRadians(i.toDouble())
            val c = cos(t); val sn = sin(t)
            val x = r + r * sign(c) * abs(c).pow(0.5)
            val y = r + r * sign(sn) * abs(sn).pow(0.5)
            if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
        }
        close()
    }
}
