package app.prayertimes.azan

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.time.ZoneId

/** واجهة لقراءة الإعدادات المحفوظة (نفس المفاتيح المستخدمة في res/xml/preferences.xml) */
class AppSettings(context: Context) {
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val lat: Double get() = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: CITIES[0].lat
    val lng: Double get() = prefs.getString(KEY_LNG, null)?.toDoubleOrNull() ?: CITIES[0].lng
    val locationName: String get() = prefs.getString(KEY_LOCATION_NAME, null) ?: CITIES[0].name
    val zone: ZoneId
        get() = runCatching { ZoneId.of(prefs.getString(KEY_TZ, null) ?: CITIES[0].tz) }
            .getOrDefault(ZoneId.systemDefault())
    val hasLocation: Boolean get() = prefs.contains(KEY_LAT)

    val method: String get() = prefs.getString(KEY_METHOD, "egypt") ?: "egypt"
    val hanafiAsr: Boolean get() = prefs.getString(KEY_ASR, "standard") == "hanafi"
    val muezzin: String get() = prefs.getString(KEY_MUEZZIN, MUEZZINS[0].id) ?: MUEZZINS[0].id
    val fajrMuezzin: String get() = prefs.getString(KEY_FAJR_MUEZZIN, DEFAULT_FAJR) ?: DEFAULT_FAJR
    val volume: Float get() = prefs.getInt(KEY_VOLUME, 90) / 100f

    fun location() = PrayerSchedule.Location(lat, lng, zone, method, hanafiAsr, adjustments)

    fun muezzinFor(p: Prayer): String =
        if (p == Prayer.FAJR && fajrMuezzin != SAME) fajrMuezzin else muezzin

    fun isEnabled(p: Prayer): Boolean = p.hasAzan && prefs.getBoolean("enabled_${p.key}", true)
    fun setEnabled(p: Prayer, on: Boolean) = prefs.edit().putBoolean("enabled_${p.key}", on).apply()

    val adjustments: Map<Prayer, Int>
        get() = Prayer.entries.associateWith { p ->
            prefs.getString("adjust_${p.key}", "0")?.trim()?.toIntOrNull() ?: 0
        }

    fun customUrl(id: String): String? = prefs.getString("url_$id", null)?.takeIf { it.isNotBlank() }
    fun setCustomUrl(id: String, url: String?) {
        prefs.edit().apply { if (url.isNullOrBlank()) remove("url_$id") else putString("url_$id", url.trim()) }.apply()
    }

    fun setLocation(lat: Double, lng: Double, name: String, tz: String) {
        prefs.edit()
            .putString(KEY_LAT, lat.toString())
            .putString(KEY_LNG, lng.toString())
            .putString(KEY_LOCATION_NAME, name)
            .putString(KEY_TZ, tz)
            .apply()
    }

    companion object {
        const val SAME = "same"
        const val DEFAULT_FAJR = "malekfajr"
        const val KEY_LAT = "lat"
        const val KEY_LNG = "lng"
        const val KEY_LOCATION_NAME = "location_name"
        const val KEY_TZ = "tz"
        const val KEY_CITY = "city"
        const val KEY_METHOD = "method"
        const val KEY_ASR = "asr"
        const val KEY_MUEZZIN = "muezzin"
        const val KEY_FAJR_MUEZZIN = "fajr_muezzin"
        const val KEY_VOLUME = "volume"
    }
}
