package app.azkar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** خلفية رق بتدرج لوني ونقش هندسي (نجمة ثمانية متكررة) خفيف */
class ParchmentDrawable(ctx: Context) : Drawable() {
    private val top = ContextCompat.getColor(ctx, R.color.parchment_top)
    private val bottom = ContextCompat.getColor(ctx, R.color.parchment_bottom)
    private val patternColor = ContextCompat.getColor(ctx, R.color.pattern)
    private val night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    private val dp = ctx.resources.displayMetrics.density
    private val bg = Paint()
    private val pattern = Paint().apply { shader = BitmapShader(tile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }

    private fun tile(): Bitmap {
        val s = (72 * dp).toInt()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.1f * dp; color = patternColor
            alpha = if (night) 22 else 38
        }
        val mid = s / 2f
        val r = 17f * dp
        fun square(rot: Double) = Path().apply {
            for (i in 0..3) {
                val a = rot + i * PI / 2
                val x = mid + r * cos(a).toFloat()
                val y = mid + r * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        c.drawPath(square(PI / 4), p)
        c.drawPath(square(0.0), p)
        c.drawLine(0f, mid, mid - r, mid, p); c.drawLine(mid + r, mid, s.toFloat(), mid, p)
        c.drawLine(mid, 0f, mid, mid - r, p); c.drawLine(mid, mid + r, mid, s.toFloat(), p)
        val cr = 6f * dp
        for ((x, y) in listOf(0f to 0f, s.toFloat() to 0f, 0f to s.toFloat(), s.toFloat() to s.toFloat())) c.drawCircle(x, y, cr, p)
        return bmp
    }

    override fun onBoundsChange(bounds: Rect) {
        bg.shader = LinearGradient(0f, bounds.top.toFloat(), 0f, bounds.bottom.toFloat(), top, bottom, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, bg)
        canvas.drawRect(bounds, pattern)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
