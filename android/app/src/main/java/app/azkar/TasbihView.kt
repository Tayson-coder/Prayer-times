package app.azkar

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * مسبحة: حبات حول دائرة، وحبة مميزة تتقدم مع كل ضغطة، والعدد المتبقي في المنتصف.
 * عند انتهاء العدد تظهر علامة صح.
 */
class TasbihView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val beads = 18
    private val dp = resources.displayMetrics.density
    private val beadColor = ContextCompat.getColor(ctx, R.color.bead)
    private val beadLight = ContextCompat.getColor(ctx, R.color.bead_light)
    private val markerColor = ContextCompat.getColor(ctx, R.color.bead_marker)
    private val ink = ContextCompat.getColor(ctx, R.color.ink)
    private val accent = ContextCompat.getColor(ctx, R.color.accent)

    private val string = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.2f * dp; color = ContextCompat.getColor(ctx, R.color.line)
    }
    private val bead = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tassel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = markerColor; strokeWidth = 2f * dp; strokeCap = Paint.Cap.ROUND }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(ctx, R.font.plex_medium)
    }
    private val check = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; style = Paint.Style.STROKE; strokeWidth = 4f * dp; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private var remaining = 1
    private var markerPos = 0f
    private var animator: ValueAnimator? = null

    fun set(remaining: Int, taps: Int, animate: Boolean) {
        this.remaining = remaining
        val target = (taps % beads).toFloat()
        animator?.cancel()
        if (animate) {
            val from = markerPos
            val to = if (target < from) target + beads else target
            animator = ValueAnimator.ofFloat(from, to).apply {
                duration = 220; interpolator = DecelerateInterpolator()
                addUpdateListener { markerPos = (it.animatedValue as Float) % beads; invalidate() }
                start()
            }
        } else {
            markerPos = target
            invalidate()
        }
        contentDescription = if (remaining == 0) context.getString(R.string.dhikr_done)
        else context.getString(R.string.dhikr_left, remaining.toString())
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val ring = size * 0.36f
        val r = size * 0.048f
        canvas.drawCircle(cx, cy, ring, string)

        val step = 2 * PI / beads
        for (i in 0 until beads) {
            val a = -i * step
            val x = cx + ring * cos(a).toFloat()
            val y = cy + ring * sin(a).toFloat()
            bead.shader = RadialGradient(x - r * 0.35f, y - r * 0.35f, r * 1.4f, beadLight, beadColor, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, r, bead)
        }
        // الحبة المميزة مع شرّابة صغيرة
        val a = -markerPos * step
        val mx = cx + ring * cos(a).toFloat()
        val my = cy + ring * sin(a).toFloat()
        canvas.drawLine(mx, my, mx + 15f * dp * cos(a).toFloat(), my + 15f * dp * sin(a).toFloat(), tassel)
        canvas.drawCircle(mx + 17f * dp * cos(a).toFloat(), my + 17f * dp * sin(a).toFloat(), 3f * dp, tassel)
        marker.shader = RadialGradient(mx - r * 0.4f, my - r * 0.4f, r * 2f, 0xFFE9B985.toInt(), markerColor, Shader.TileMode.CLAMP)
        canvas.drawCircle(mx, my, r * 1.45f, marker)

        if (remaining == 0) {
            val s = size * 0.1f
            val p = Path().apply { moveTo(cx - s, cy); lineTo(cx - s * 0.25f, cy + s * 0.75f); lineTo(cx + s * 1.1f, cy - s * 0.7f) }
            canvas.drawPath(p, check)
        } else {
            number.textSize = if (remaining >= 100) size * 0.2f else size * 0.25f
            canvas.drawText(remaining.toString(), cx, cy - (number.ascent() + number.descent()) / 2f, number)
        }
    }
}
