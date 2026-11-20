package app.prayertimes.azan

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * لون السماء في الفترة الحالية، ومسار الشمس من الشروق إلى المغرب
 * مع علامات الظهر والعصر وموضع الشمس الآن (أو الهلال ليلاً).
 * الشروق على اليمين والمغرب على اليسار، باتجاه القراءة العربية.
 */
class SkyView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    enum class Period(val top: Int, val bottom: Int) {
        NIGHT(0xFF0B1730.toInt(), 0xFF22365F.toInt()),
        FAJR(0xFF1F2B57.toInt(), 0xFF5D63A0.toInt()),
        MORNING(0xFF3A73B3.toInt(), 0xFFA9CDEB.toInt()),
        DHUHR(0xFF2C6FB0.toInt(), 0xFF8CC0E6.toInt()),
        ASR(0xFFA86C27.toInt(), 0xFFE0B66C.toInt()),
        MAGHRIB(0xFF5E2E52.toInt(), 0xFFD9826A.toInt()),
    }

    private var period = Period.DHUHR
    private var marks = floatArrayOf(0f, 0.5f, 0.72f, 1f)
    private var sunT = 0.5f
    private var isDay = true

    private val dp = resources.displayMetrics.density
    private val bg = Paint()
    private val horizon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x59FFFFFF; strokeWidth = 1f * dp; style = Paint.Style.STROKE
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x8CFFFFFF.toInt(); strokeWidth = 1.5f * dp; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; pathEffect = DashPathEffect(floatArrayOf(0.1f, 5f * dp), 0f)
    }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt() }
    private val sunGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFF0C8 }
    private val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF3D6.toInt() }
    private val moon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF1E7C8.toInt() }
    private val path = Path()

    /** مقدار تداخل اللوح السفلي فوق السماء */
    var overlap = 24f * dp

    fun update(times: Map<Prayer, Instant>, ishaYesterdayOrToday: Instant, now: Instant) {
        val sunrise = times.getValue(Prayer.SUNRISE).toEpochMilli()
        val maghrib = times.getValue(Prayer.MAGHRIB).toEpochMilli()
        val span = (maghrib - sunrise).coerceAtLeast(1).toFloat()
        fun frac(p: Prayer) = ((times.getValue(p).toEpochMilli() - sunrise) / span).coerceIn(0f, 1f)
        marks = floatArrayOf(0f, frac(Prayer.DHUHR), frac(Prayer.ASR), 1f)
        val n = now.toEpochMilli()
        sunT = ((n - sunrise) / span)
        isDay = sunT in 0f..1f
        period = when {
            now.isBefore(times.getValue(Prayer.FAJR)) -> Period.NIGHT
            now.isBefore(times.getValue(Prayer.SUNRISE)) -> Period.FAJR
            now.isBefore(times.getValue(Prayer.DHUHR)) -> Period.MORNING
            now.isBefore(times.getValue(Prayer.ASR)) -> Period.DHUHR
            now.isBefore(times.getValue(Prayer.MAGHRIB)) -> Period.ASR
            now.isBefore(ishaYesterdayOrToday) -> Period.MAGHRIB
            else -> Period.NIGHT
        }
        bg.shader = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bg.shader = null
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (bg.shader == null) bg.shader = LinearGradient(0f, 0f, 0f, h, period.top, period.bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, bg)

        val baseY = h - overlap - 12f * dp
        val rx = w / 2f - 40f * dp
        val ry = min(rx * 0.5f, 78f * dp)
        val cx = w / 2f
        fun x(t: Float) = cx + rx * cos(PI * t).toFloat()
        fun y(t: Float) = baseY - ry * sin(PI * t).toFloat()

        canvas.drawLine(16f * dp, baseY, w - 16f * dp, baseY, horizon)
        path.reset()
        for (i in 0..64) {
            val t = i / 64f
            if (i == 0) path.moveTo(x(t), y(t)) else path.lineTo(x(t), y(t))
        }
        canvas.drawPath(path, arc)
        for (t in marks) canvas.drawCircle(x(t), y(t), 3.5f * dp, mark)

        if (isDay) {
            canvas.drawCircle(x(sunT), y(sunT), 16f * dp, sunGlow)
            canvas.drawCircle(x(sunT), y(sunT), 9f * dp, sun)
        } else {
            // هلال أعلى يمين القوس
            val mx = w - 64f * dp
            val my = baseY - ry - 4f * dp
            val r = 13f * dp
            val outer = Path().apply { addCircle(mx, my, r, Path.Direction.CW) }
            val inner = Path().apply { addCircle(mx - r * 0.45f, my - r * 0.25f, r * 0.85f, Path.Direction.CW) }
            outer.op(inner, Path.Op.DIFFERENCE)
            canvas.drawPath(outer, moon)
        }
    }
}
