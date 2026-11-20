package app.azkar

import android.content.SharedPreferences
import android.os.Bundle
import android.text.format.DateFormat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.settings_container, SettingsFragment()).commit()
        }
    }
}

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val arabic = Locale("ar", "EG")
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", arabic).withDecimalStyle(DecimalStyle.of(arabic))

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        bindTime(Settings.K_MORNING_TIME, LocalTime.of(6, 30))
        bindTime(Settings.K_EVENING_TIME, LocalTime.of(17, 0))
    }

    private fun bindTime(key: String, fallback: LocalTime) {
        val pref = findPreference<Preference>(key) ?: return
        val prefs = preferenceManager.sharedPreferences!!
        fun current() = AzkarTimes.parseTime(prefs.getString(key, null), fallback)
        pref.summary = timeFmt.format(current())
        pref.setOnPreferenceClickListener {
            val t = current()
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(if (DateFormat.is24HourFormat(requireContext())) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(t.hour).setMinute(t.minute)
                .setTitleText(pref.title)
                .build()
            picker.addOnPositiveButtonClickListener {
                val chosen = LocalTime.of(picker.hour, picker.minute)
                prefs.edit().putString(key, chosen.toString()).apply()
                pref.summary = timeFmt.format(chosen)
            }
            picker.show(parentFragmentManager, key)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        Reminders.scheduleAll(requireContext())
    }
}
