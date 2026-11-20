package app.azkar

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import java.time.LocalTime
import java.time.ZoneId

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        Reminders.createChannels(this)
        Reminders.scheduleAll(this)
    }
}

/** كل الشاشات من اليمين لليسار وعلى خلفية الرق */
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        findViewById<View>(android.R.id.content).background = ParchmentDrawable(this)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

/** الأذكار من assets/azkar.json (تُقرأ مرة واحدة) */
object Azkar {
    @Volatile private var cache: List<Dhikr>? = null

    fun all(ctx: Context): List<Dhikr> = cache ?: synchronized(this) {
        cache ?: AzkarData.parse(ctx.assets.open("azkar.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
            .also { cache = it }
    }

    fun of(ctx: Context, kind: AzkarKind) = AzkarData.of(all(ctx), kind)
}

class Settings(ctx: Context) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
    val zone: ZoneId get() = ZoneId.systemDefault()

    val remindMorning get() = prefs.getBoolean(K_REMIND_MORNING, true)
    val remindEvening get() = prefs.getBoolean(K_REMIND_EVENING, true)
    val morningTime: LocalTime get() = AzkarTimes.parseTime(prefs.getString(K_MORNING_TIME, null), LocalTime.of(6, 30))
    val eveningTime: LocalTime get() = AzkarTimes.parseTime(prefs.getString(K_EVENING_TIME, null), LocalTime.of(17, 0))
    val dhikrNotifications get() = prefs.getBoolean(K_DHIKR, true)
    val dhikrInterval get() = prefs.getString(K_DHIKR_INTERVAL, "2")?.toIntOrNull() ?: 2
    val textSizeSp: Float get() = when (prefs.getString(K_TEXT_SIZE, "medium")) { "small" -> 26f; "large" -> 36f; "xlarge" -> 42f; else -> 31f }
    val vibrate get() = prefs.getBoolean(K_VIBRATE, true)

    // مواقيت الصلاة
    val hasLocation get() = prefs.contains(K_LAT)
    val lat get() = prefs.getString(K_LAT, null)?.toDoubleOrNull() ?: CITIES[0].lat
    val lng get() = prefs.getString(K_LNG, null)?.toDoubleOrNull() ?: CITIES[0].lng
    val locationName: String get() = prefs.getString(K_LOCATION_NAME, null) ?: CITIES[0].name
    val prayerZone: ZoneId get() = runCatching { ZoneId.of(prefs.getString(K_TZ, null) ?: CITIES[0].tz) }.getOrDefault(ZoneId.systemDefault())
    val method get() = prefs.getString(K_METHOD, "egypt") ?: "egypt"
    val hanafiAsr get() = prefs.getString(K_ASR, "standard") == "hanafi"
    val prayerAlerts get() = prefs.getBoolean(K_PRAYER_ALERTS, true)
    fun location() = PrayerSchedule.Location(lat, lng, prayerZone, method, hanafiAsr, emptyMap())
    fun setLocation(lat: Double, lng: Double, name: String, tz: String) = prefs.edit()
        .putString(K_LAT, lat.toString()).putString(K_LNG, lng.toString())
        .putString(K_LOCATION_NAME, name).putString(K_TZ, tz).apply()
    val autoAdvance get() = prefs.getBoolean(K_AUTO_ADVANCE, true)

    fun reminderOn(kind: AzkarKind) = if (kind == AzkarKind.MORNING) remindMorning else remindEvening
    fun reminderTime(kind: AzkarKind) = if (kind == AzkarKind.MORNING) morningTime else eveningTime
    fun setReminder(kind: AzkarKind, on: Boolean) =
        prefs.edit().putBoolean(if (kind == AzkarKind.MORNING) K_REMIND_MORNING else K_REMIND_EVENING, on).apply()

    companion object {
        const val K_REMIND_MORNING = "remind_morning"
        const val K_REMIND_EVENING = "remind_evening"
        const val K_MORNING_TIME = "morning_time"
        const val K_EVENING_TIME = "evening_time"
        const val K_DHIKR = "dhikr_notifications"
        const val K_DHIKR_INTERVAL = "dhikr_interval"
        const val K_TEXT_SIZE = "text_size"
        const val K_VIBRATE = "vibrate"
        const val K_AUTO_ADVANCE = "auto_advance"
        const val K_LAT = "lat"
        const val K_LNG = "lng"
        const val K_LOCATION_NAME = "location_name"
        const val K_TZ = "tz"
        const val K_CITY = "city"
        const val K_METHOD = "method"
        const val K_ASR = "asr"
        const val K_PRAYER_ALERTS = "prayer_alerts"
    }
}

/** ما بقي من كل ذكر اليوم؛ يبدأ من جديد كل يوم */
class Progress(ctx: Context, private val kind: AzkarKind) {
    private val prefs = Settings(ctx).prefs
    private val list = Azkar.of(ctx, kind)
    private val key = "progress_${kind.name}"
    private val today = java.time.LocalDate.now().toString()
    val remaining: IntArray = load()

    private fun load(): IntArray {
        val saved = prefs.getString(key, null)?.split("|")
        if (saved != null && saved.size == 2 && saved[0] == today) {
            val counts = saved[1].split(",").mapNotNull { it.toIntOrNull() }
            if (counts.size == list.size) return counts.toIntArray()
        }
        return list.map { it.count }.toIntArray()
    }

    fun save() = prefs.edit().putString(key, "$today|${remaining.joinToString(",")}").apply()
    fun reset(i: Int) { remaining[i] = list[i].count; save() }
    fun resetAll() { list.forEachIndexed { i, d -> remaining[i] = d.count }; save() }
    val done get() = remaining.count { it == 0 }
    val total get() = list.size
    val firstUnfinished get() = remaining.indexOfFirst { it > 0 }.let { if (it < 0) 0 else it }
}
