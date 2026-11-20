package app.prayertimes.azan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", arabic).withDecimalStyle(DecimalStyle.of(arabic))
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: AppSettings
    private lateinit var preview: AzanPlayer
    private lateinit var sky: SkyView
    private var renderedDay: LocalDate? = null
    private var renderedKey: String? = null
    private var lastMinute = -1L
    private val rows = mutableMapOf<Prayer, View>()

    private val kufiBold by lazy { ResourcesCompat.getFont(this, R.font.kufi_bold) }
    private val kufiMedium by lazy { ResourcesCompat.getFont(this, R.font.kufi_medium) }
    private val plexMedium by lazy { ResourcesCompat.getFont(this, R.font.plex_medium) }
    private val plexSemibold by lazy { ResourcesCompat.getFont(this, R.font.plex_semibold) }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { updateWarnings() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        settings = AppSettings(this)
        preview = AzanPlayer(this)
        sky = findViewById(R.id.sky)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false // نص أبيض فوق السماء
            isAppearanceLightNavigationBars = !night
        }
        val hero = findViewById<View>(R.id.hero)
        val sheet = findViewById<View>(R.id.sheet)
        val heroTop = hero.paddingTop
        val sheetBottom = sheet.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scroll)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            hero.updatePadding(top = heroTop + bars.top)
            sheet.updatePadding(bottom = sheetBottom + bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.stop_azan_button).setOnClickListener {
            startService(AzanService.stopIntent(this))
            preview.stop()
            tick()
        }
        findViewById<View>(R.id.muezzin_row).setOnClickListener { chooseMuezzin() }
        findViewById<ImageButton>(R.id.preview_button).setOnClickListener { togglePreview() }
        findViewById<Button>(R.id.manage_audio_button).setOnClickListener {
            startActivity(Intent(this, AudioActivity::class.java))
        }
        findViewById<Button>(R.id.download_voice_button).setOnClickListener { downloadVoice(settings.muezzin) }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        downloadDefaults()
        if (!settings.hasLocation) startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_FIRST_RUN, true))
    }

    override fun onResume() {
        super.onResume()
        renderedKey = null
        AzanScheduler.schedule(this)
        updateMuezzin()
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

    // ---------- المؤذن ----------

    private fun chooseMuezzin() {
        val current = MUEZZINS.indexOfFirst { it.id == settings.muezzin }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_muezzin)
            .setSingleChoiceItems(MUEZZINS.map { it.name }.toTypedArray(), current) { dialog, which ->
                val m = MUEZZINS[which]
                if (m.id != settings.muezzin) {
                    preview.stop()
                    settings.prefs.edit().putString(AppSettings.KEY_MUEZZIN, m.id).apply()
                    if (AzanAudio.isDownloadable(this, m.id)) downloadVoice(m.id)
                }
                updateMuezzin()
                dialog.dismiss()
            }
            .show()
    }

    private fun togglePreview() {
        if (preview.isPlaying) {
            preview.stop()
        } else {
            preview.play(settings.muezzin, onFinished = { updatePreviewButton() })
        }
        updatePreviewButton()
    }

    private fun updatePreviewButton() {
        findViewById<ImageButton>(R.id.preview_button).apply {
            val playing = preview.isPlaying
            setImageResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play)
            contentDescription = getString(if (playing) R.string.stop else R.string.listen)
        }
    }

    private fun downloadVoice(id: String) {
        AzanDownloader.download(this, id) { ok ->
            if (isDestroyed) return@download
            Toast.makeText(this, if (ok) R.string.voice_downloaded else R.string.download_failed, Toast.LENGTH_SHORT).show()
            updateMuezzin()
        }
        updateMuezzin()
    }

    /** أول تشغيل: تحميل صوت المؤذن الافتراضي ومؤذن الفجر إن لم يكونا مدمجين */
    private fun downloadDefaults() {
        listOf(settings.muezzin, settings.muezzinFor(Prayer.FAJR)).distinct()
            .filter { AzanAudio.isDownloadable(this, it) }
            .forEach { id -> AzanDownloader.download(this, id) { if (!isDestroyed) updateMuezzin() } }
    }

    private fun updateMuezzin() {
        val id = settings.muezzin
        findViewById<TextView>(R.id.muezzin_name).text = muezzinById(id).name
        val label = AzanAudio.describe(this, id)
        val downloading = AzanDownloader.isDownloading(id)
        // لا نعرض مصدر الصوت إلا إذا كان يحتاج انتباهاً
        val note = when {
            downloading -> getString(R.string.downloading)
            label != null -> null
            muezzinById(id).url != null -> getString(R.string.not_downloaded_yet)
            else -> getString(R.string.no_audio_yet)
        }
        findViewById<TextView>(R.id.muezzin_source).apply {
            text = note
            visibility = if (note == null) View.GONE else View.VISIBLE
        }
        findViewById<Button>(R.id.download_voice_button).apply {
            visibility = if (AzanAudio.isDownloadable(this@MainActivity, id)) View.VISIBLE else View.GONE
            isEnabled = !downloading
        }
        updatePreviewButton()
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

    // ---------- المواقيت ----------

    private fun tick() {
        val now = Instant.now()
        val loc = settings.location()
        val day = PrayerSchedule.today(loc, now)
        val key = "${settings.prefs.all.hashCode()}"
        if (day != renderedDay || key != renderedKey) {
            renderedDay = day
            renderedKey = key
            lastMinute = -1
            renderDay(loc, day)
        }
        val times = PrayerSchedule.day(loc, day)
        val (next, at) = PrayerSchedule.nextPrayer(loc, now)
        val secs = Duration.between(now, at).coerceAtLeast(Duration.ZERO).seconds
        findViewById<TextView>(R.id.next_name).text = next.arabic
        findViewById<TextView>(R.id.countdown).text = getString(
            R.string.in_time, String.format(arabic, "%d:%02d:%02d", secs / 3600, secs % 3600 / 60, secs % 60),
        )
        findViewById<TextView>(R.id.next_time).text = getString(R.string.azan_at, timeFmt.format(at.atZone(loc.zone)))

        // السماء وحالة الصفوف تتغير كل دقيقة فقط
        val minute = now.epochSecond / 60
        if (minute != lastMinute) {
            lastMinute = minute
            val isha = times.getValue(Prayer.ISHA)
            sky.update(times, isha, now)
            val current = Prayer.entries.lastOrNull { !times.getValue(it).isAfter(now) }
            val nextToday = if (times.getValue(next) == at) next else null
            for ((p, row) in rows) {
                styleRow(row, when {
                    p == current -> RowState.CURRENT
                    p == nextToday -> RowState.NEXT
                    times.getValue(p).isBefore(now) -> RowState.PAST
                    else -> RowState.UPCOMING
                })
            }
        }

        val playing = AzanService.current
        findViewById<View>(R.id.azan_banner).visibility = if (playing != null) View.VISIBLE else View.GONE
        if (playing != null) findViewById<TextView>(R.id.azan_banner_text).text = getString(R.string.azan_now, playing.arabic)
    }

    private enum class RowState { PAST, CURRENT, NEXT, UPCOMING }

    private fun styleRow(row: View, state: RowState) {
        val name = row.findViewById<TextView>(R.id.prayer_name)
        val time = row.findViewById<TextView>(R.id.prayer_time)
        val color = ContextCompat.getColor(this, when (state) {
            RowState.PAST -> R.color.muted
            RowState.CURRENT -> R.color.turquoise
            else -> R.color.ink
        })
        name.setTextColor(color)
        time.setTextColor(color)
        val strong = state == RowState.NEXT
        name.typeface = if (strong) kufiBold else kufiMedium
        time.typeface = if (strong) plexSemibold else plexMedium
        row.findViewById<View>(R.id.current_bar).visibility = if (state == RowState.CURRENT) View.VISIBLE else View.INVISIBLE
    }

    private fun renderDay(loc: PrayerSchedule.Location, day: LocalDate) {
        findViewById<TextView>(R.id.location_name).text = settings.locationName
        findViewById<TextView>(R.id.hijri_date).text = hijri(loc)
        findViewById<TextView>(R.id.greg_date).text = dateFmt.format(day)

        val list = findViewById<LinearLayout>(R.id.times_list)
        list.removeAllViews()
        rows.clear()
        val times = PrayerSchedule.day(loc, day)
        for ((i, p) in Prayer.entries.withIndex()) {
            val row = layoutInflater.inflate(R.layout.item_prayer, list, false)
            row.findViewById<TextView>(R.id.prayer_name).text = p.arabic
            row.findViewById<TextView>(R.id.prayer_time).text = timeFmt.format(times.getValue(p).atZone(loc.zone))
            row.findViewById<View>(R.id.divider).visibility = if (i == Prayer.entries.lastIndex) View.GONE else View.VISIBLE
            val bell = row.findViewById<ImageButton>(R.id.prayer_bell)
            if (p.hasAzan) {
                fun paint() {
                    val on = settings.isEnabled(p)
                    bell.setImageResource(if (on) R.drawable.ic_bell else R.drawable.ic_bell_off)
                    bell.alpha = if (on) 0.85f else 0.45f
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
                bell.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            list.addView(row)
            rows[p] = row
        }
    }

    private fun hijri(loc: PrayerSchedule.Location): String = runCatching {
        val cal = IslamicCalendar(ULocale("ar@calendar=islamic-umalqura"))
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.timeZone = android.icu.util.TimeZone.getTimeZone(loc.zone.id)
        val df = android.icu.text.DateFormat.getDateInstance(cal, android.icu.text.DateFormat.LONG, Locale("ar"))
        df.timeZone = cal.timeZone
        df.format(Date())
    }.getOrDefault("")
}
