package app.azkar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

/** الشاشة الرئيسية: أذكار الصباح وأذكار المساء، والمناسبة للوقت مميزة */
class MainActivity : BaseActivity() {
    private val arabic = Locale("ar", "EG")
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.morning_card).setOnClickListener { startActivity(ReaderActivity.intent(this, AzkarKind.MORNING)) }
        findViewById<View>(R.id.evening_card).setOnClickListener { startActivity(ReaderActivity.intent(this, AzkarKind.EVENING)) }

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
