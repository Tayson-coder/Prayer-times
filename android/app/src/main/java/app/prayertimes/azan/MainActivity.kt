package app.prayertimes.azan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Date
import java.util.Locale

class MainActivity : BaseActivity() {
    private val arabic = Locale("ar", "EG")
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", arabic).withDecimalStyle(DecimalStyle.of(arabic))
    private val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", arabic).withDecimalStyle(DecimalStyle.of(arabic))
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: AppSettings
    private lateinit var preview: AzanPlayer
    private var renderedDay: LocalDate? = null
    private var renderedKey: String? = null
    private val rows = mutableMapOf<Prayer, View>()

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateWarnings() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = AppSettings(this)
        preview = AzanPlayer(this)

        findViewById<ImageButton>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.stop_azan_button).setOnClickListener {
            startService(AzanService.stopIntent(this))
            preview.stop()
            tick()
        }
        setupMuezzinPicker()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        if (!settings.hasLocation) startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_FIRST_RUN, true))
    }

    override fun onResume() {
        super.onResume()
        renderedKey = null
        AzanScheduler.schedule(this)
        findViewById<Spinner>(R.id.muezzin_spinner).setSelection(MUEZZINS.indexOfFirst { it.id == settings.muezzin }.coerceAtLeast(0))
        updateSource()
        updateWarnings()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        preview.stop()
        super.onDestroy()
    }

    private fun setupMuezzinPicker() {
        val spinner = findViewById<Spinner>(R.id.muezzin_spinner)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, MUEZZINS.map { it.name })
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val m = MUEZZINS[position]
                if (m.id != settings.muezzin) {
                    settings.prefs.edit().putString(AppSettings.KEY_MUEZZIN, m.id).apply()
                }
                updateSource()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val button = findViewById<Button>(R.id.preview_button)
        button.setOnClickListener {
            if (preview.isPlaying) {
                preview.stop()
                button.setText(R.string.listen)
            } else {
                button.setText(R.string.stop)
                preview.play(settings.muezzin, onFinished = { button.setText(R.string.listen) })
            }
        }
        findViewById<Button>(R.id.manage_audio_button).setOnClickListener {
            startActivity(Intent(this, AudioActivity::class.java))
        }
    }

    private fun updateSource() {
        val label = AzanAudio.describe(this, settings.muezzin)
        findViewById<TextView>(R.id.muezzin_source).text =
            if (label != null) getString(R.string.source_is, label) else getString(R.string.no_audio_yet)
    }

    private fun updateWarnings() {
        val box = findViewById<LinearLayout>(R.id.warnings)
        box.removeAllViews()
        fun add(text: Int, action: () -> Unit) {
            val v = layoutInflater.inflate(R.layout.item_warning, box, false)
            v.findViewById<TextView>(R.id.warning_text).setText(text)
            v.findViewById<Button>(R.id.warning_button).setOnClickListener { action() }
            box.addView(v)
        }
        if (!AzanScheduler.canScheduleExact(this) && Build.VERSION.SDK_INT >= 31) {
            add(R.string.warn_exact) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            add(R.string.warn_notifications) {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
        box.visibility = if (box.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun tick() {
        val now = Instant.now()
        val loc = settings.location()
        val day = PrayerSchedule.today(loc, now)
        val key = "${settings.prefs.all.hashCode()}"
        if (day != renderedDay || key != renderedKey) {
            renderedDay = day
            renderedKey = key
            renderDay(loc, day)
        }
        val times = PrayerSchedule.day(loc, day)
        val (next, at) = PrayerSchedule.nextPrayer(loc, now)
        val left = Duration.between(now, at).coerceAtLeast(Duration.ZERO)
        findViewById<TextView>(R.id.next_name).text = next.arabic
        findViewById<TextView>(R.id.next_time).text = timeFmt.format(at.atZone(loc.zone))
        val secs = left.seconds
        findViewById<TextView>(R.id.countdown).text =
            String.format(Locale.US, "%02d:%02d:%02d", secs / 3600, secs % 3600 / 60, secs % 60)

        val current = Prayer.entries.lastOrNull { !times.getValue(it).isAfter(now) }
        for ((p, row) in rows) {
            row.isActivated = p == current
            row.isSelected = p == next && times.getValue(p) == at
        }

        val playing = AzanService.current
        val banner = findViewById<View>(R.id.azan_banner)
        banner.visibility = if (playing != null) View.VISIBLE else View.GONE
        if (playing != null) findViewById<TextView>(R.id.azan_banner_text).text = getString(R.string.azan_now, playing.arabic)
    }

    private fun renderDay(loc: PrayerSchedule.Location, day: LocalDate) {
        findViewById<TextView>(R.id.location_name).text = settings.locationName
        findViewById<TextView>(R.id.greg_date).text = dateFmt.format(day)
        findViewById<TextView>(R.id.hijri_date).text = hijri(loc)

        val list = findViewById<LinearLayout>(R.id.times_list)
        list.removeAllViews()
        rows.clear()
        val times = PrayerSchedule.day(loc, day)
        for (p in Prayer.entries) {
            val row = layoutInflater.inflate(R.layout.item_prayer, list, false)
            row.findViewById<TextView>(R.id.prayer_name).text = p.arabic
            row.findViewById<TextView>(R.id.prayer_time).text = timeFmt.format(times.getValue(p).atZone(loc.zone))
            val bell = row.findViewById<ImageButton>(R.id.prayer_bell)
            if (p.hasAzan) {
                fun paint() {
                    val on = settings.isEnabled(p)
                    bell.setImageResource(if (on) R.drawable.ic_bell else R.drawable.ic_bell_off)
                    bell.alpha = if (on) 1f else 0.4f
                    bell.contentDescription = getString(if (on) R.string.azan_on else R.string.azan_off, p.arabic)
                }
                paint()
                bell.setOnClickListener {
                    settings.setEnabled(p, !settings.isEnabled(p))
                    paint()
                    renderedKey = "${settings.prefs.all.hashCode()}"
                    AzanScheduler.schedule(this)
                }
            } else {
                bell.visibility = View.INVISIBLE
            }
            list.addView(row)
            rows[p] = row
        }
    }

    private fun hijri(loc: PrayerSchedule.Location): String = runCatching {
        val cal = IslamicCalendar(ULocale("ar@calendar=islamic-umalqura"))
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.timeZone = android.icu.util.TimeZone.getTimeZone(loc.zone.id)
        val df = android.icu.text.DateFormat.getDateInstance(cal, android.icu.text.DateFormat.FULL, Locale("ar"))
        df.timeZone = cal.timeZone
        df.format(Date())
    }.getOrDefault("")
}
