package app.azkar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Date
import java.util.Locale

/** الشاشة الرئيسية: أذكار الصباح وأذكار المساء، والمناسبة للوقت مميزة */
class MainActivity : BaseActivity() {
    private val arabic = Locale("ar", "EG")
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm", arabic).withDecimalStyle(DecimalStyle.of(arabic))
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { tickPrayer(); handler.postDelayed(this, 1000) }
    }
    private var renderedDay: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.morning_card).setOnClickListener { startActivity(ReaderActivity.intent(this, AzkarKind.MORNING)) }
        findViewById<View>(R.id.evening_card).setOnClickListener { startActivity(ReaderActivity.intent(this, AzkarKind.EVENING)) }
        findViewById<View>(R.id.tasbih_card).setOnClickListener { startActivity(Intent(this, TasbihActivity::class.java)) }
        findViewById<View>(R.id.prayer_card).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.hijri).text = hijri()
        val now = AzkarTimes.suggested(ZonedDateTime.now())
        bind(AzkarKind.MORNING, R.id.morning_card, R.id.morning_progress, R.id.morning_bar, now)
        bind(AzkarKind.EVENING, R.id.evening_card, R.id.evening_progress, R.id.evening_bar, now)
        renderedDay = null
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    /** بطاقة المواقيت: الصلاة القادمة والعدّ التنازلي، ومواقيت اليوم مع تمييز الحالية */
    private fun tickPrayer() {
        val s = Settings(this)
        val loc = s.location()
        val now = Instant.now()
        val day = PrayerSchedule.today(loc, now)
        val times = PrayerSchedule.day(loc, day)
        val (next, at) = PrayerSchedule.nextPrayer(loc, now)
        val secs = Duration.between(now, at).coerceAtLeast(Duration.ZERO).seconds
        findViewById<TextView>(R.id.next_prayer).text = next.arabic
        findViewById<TextView>(R.id.next_countdown).text =
            getString(R.string.next_in, String.format(arabic, "%d:%02d:%02d", secs / 3600, secs % 3600 / 60, secs % 60))
        findViewById<TextView>(R.id.prayer_city).text = if (s.hasLocation) s.locationName else getString(R.string.choose_city)

        val key = "$day|${loc.lat}|${loc.lng}|${loc.method}|${loc.hanafiAsr}|${now.epochSecond / 60}"
        if (key == renderedDay) return
        renderedDay = key
        val current = Prayer.entries.lastOrNull { !times.getValue(it).isAfter(now) }
        val rows = listOf(findViewById<LinearLayout>(R.id.times_row1), findViewById(R.id.times_row2))
        rows.forEach { it.removeAllViews() }
        Prayer.entries.forEachIndexed { i, p ->
            val row = rows[i / 3]
            val cell = layoutInflater.inflate(R.layout.item_time_cell, row, false)
            val color = ContextCompat.getColor(this, if (p == current) R.color.accent else R.color.ink)
            cell.findViewById<TextView>(R.id.cell_name).apply {
                text = p.arabic
                setTextColor(ContextCompat.getColor(context, if (p == current) R.color.accent else R.color.muted))
            }
            cell.findViewById<TextView>(R.id.cell_time).apply {
                text = timeFmt.format(times.getValue(p).atZone(loc.zone))
                setTextColor(color)
            }
            row.addView(cell)
        }
    }

    private fun bind(kind: AzkarKind, card: Int, label: Int, bar: Int, suggested: AzkarKind) {
        val p = Progress(this, kind)
        fun n(x: Int) = String.format(arabic, "%d", x)
        findViewById<TextView>(label).text = when (p.done) {
            0 -> getString(R.string.count_adhkar, n(p.total))
            p.total -> getString(R.string.done_today)
            else -> getString(R.string.progress, n(p.done), n(p.total))
        }
        findViewById<LinearProgressIndicator>(bar).apply {
            max = p.total
            setProgressCompat(p.done, false)
        }
        findViewById<View>(card).setBackgroundResource(if (kind == suggested) R.drawable.card_now else R.drawable.card)
    }

    private fun hijri(): String = runCatching {
        val cal = IslamicCalendar(ULocale("ar@calendar=islamic-umalqura"))
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        android.icu.text.DateFormat.getDateInstance(cal, android.icu.text.DateFormat.FULL, Locale("ar")).format(Date())
    }.getOrDefault("")
}
