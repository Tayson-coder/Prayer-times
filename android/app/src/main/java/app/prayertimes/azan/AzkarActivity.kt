package app.prayertimes.azan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import java.time.Instant
import java.util.Locale

/** تقدّم الأذكار لليوم: عدد المرات المتبقية لكل ذكر، ويبدأ من جديد كل يوم */
class AzkarProgress(ctx: Context, private val kind: AzkarKind) {
    private val settings = AppSettings(ctx)
    private val key = "azkar_progress_${kind.name}"
    private val list = azkarOf(kind)
    private val today = PrayerSchedule.today(settings.location()).toString()

    val remaining: IntArray = load()

    private fun load(): IntArray {
        val saved = settings.prefs.getString(key, null)?.split("|")
        if (saved != null && saved.size == 2 && saved[0] == today) {
            val counts = saved[1].split(",").mapNotNull { it.toIntOrNull() }
            if (counts.size == list.size) return counts.toIntArray()
        }
        return list.map { it.count }.toIntArray()
    }

    fun save() = settings.prefs.edit().putString(key, "$today|${remaining.joinToString(",")}").apply()
    fun reset() { list.forEachIndexed { i, d -> remaining[i] = d.count }; save() }
    val done: Int get() = remaining.count { it == 0 }
    val total: Int get() = list.size
}

class AzkarActivity : BaseActivity() {
    private val arabic = Locale("ar", "EG")
    private lateinit var kind: AzkarKind
    private lateinit var progress: AzkarProgress

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_azkar)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val settings = AppSettings(this)
        val fallback = AzkarTimes.suggested(PrayerSchedule.day(settings.location(), PrayerSchedule.today(settings.location())), Instant.now())
        kind = savedInstanceState?.getString(EXTRA_KIND)?.let { AzkarKind.valueOf(it) }
            ?: intent.getStringExtra(EXTRA_KIND)?.let { runCatching { AzkarKind.valueOf(it) }.getOrNull() }
            ?: fallback

        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.kind_toggle)
        toggle.check(if (kind == AzkarKind.MORNING) R.id.kind_morning else R.id.kind_evening)
        toggle.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            kind = if (id == R.id.kind_morning) AzkarKind.MORNING else AzkarKind.EVENING
            render(scrollToTop = true)
        }
        render(scrollToTop = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_KIND, kind.name)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_RESET, 0, R.string.azkar_reset).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_RESET) { progress.reset(); render(scrollToTop = true); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun times(n: Int): String = when (n) {
        1 -> getString(R.string.times_once)
        2 -> getString(R.string.times_twice)
        in 3..10 -> getString(R.string.times_few, String.format(arabic, "%d", n))
        else -> getString(R.string.times_many, String.format(arabic, "%d", n))
    }

    private fun render(scrollToTop: Boolean) {
        progress = AzkarProgress(this, kind)
        supportActionBar?.title = kind.title
        val list = findViewById<LinearLayout>(R.id.azkar_list)
        list.removeAllViews()
        azkarOf(kind).forEachIndexed { i, d ->
            val row = layoutInflater.inflate(R.layout.item_dhikr, list, false)
            row.findViewById<TextView>(R.id.d_text).text = d.text
            row.findViewById<TextView>(R.id.d_meta).text =
                if (d.note.isBlank()) times(d.count) else "${times(d.count)}\n${d.note}"
            paintRow(row, i)
            row.setOnClickListener {
                if (progress.remaining[i] > 0) {
                    progress.remaining[i]--
                    progress.save()
                    row.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    paintRow(row, i)
                    updateProgress()
                }
            }
            list.addView(row)
        }
        updateProgress()
        if (scrollToTop) findViewById<View>(R.id.azkar_scroll).scrollTo(0, 0)
    }

    private fun paintRow(row: View, i: Int) {
        val left = progress.remaining[i]
        val done = left == 0
        row.findViewById<TextView>(R.id.d_count).apply {
            text = String.format(arabic, "%d", left)
            visibility = if (done) View.GONE else View.VISIBLE
        }
        row.findViewById<ImageView>(R.id.d_check).visibility = if (done) View.VISIBLE else View.GONE
        row.findViewById<View>(R.id.d_counter).isActivated = done
        row.findViewById<TextView>(R.id.d_text).setTextColor(
            ContextCompat.getColor(this, if (done) R.color.muted else R.color.ink),
        )
        row.contentDescription = if (done) getString(R.string.dhikr_done) else getString(R.string.dhikr_left, String.format(arabic, "%d", left))
    }

    private fun updateProgress() {
        findViewById<TextView>(R.id.azkar_progress).apply {
            val all = progress.done == progress.total
            text = if (all) getString(R.string.azkar_all_done, kind.title)
            else getString(R.string.azkar_progress, String.format(arabic, "%d", progress.done), String.format(arabic, "%d", progress.total))
            setTextColor(ContextCompat.getColor(context, if (all) R.color.turquoise else R.color.muted))
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        private const val MENU_RESET = 1
        fun intent(ctx: Context, kind: AzkarKind) = Intent(ctx, AzkarActivity::class.java).putExtra(EXTRA_KIND, kind.name)
    }
}
