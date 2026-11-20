package app.azkar

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.CancellationSignal
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.time.ZoneId
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
    private lateinit var settings: Settings
    private var updatingLocation = false

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) fetchLocation() else toast(R.string.location_denied)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        settings = Settings(requireContext())
        bindTime(Settings.K_MORNING_TIME, LocalTime.of(6, 30))
        bindTime(Settings.K_EVENING_TIME, LocalTime.of(17, 0))
        findPreference<ListPreference>(Settings.K_CITY)?.apply {
            entries = CITIES.map { it.name }.toTypedArray()
            entryValues = CITIES.indices.map { it.toString() }.toTypedArray()
        }
        findPreference<ListPreference>(Settings.K_METHOD)?.apply {
            entries = PrayTimes.METHODS.values.map { it.name }.toTypedArray()
            entryValues = PrayTimes.METHODS.keys.toTypedArray()
        }
        findPreference<Preference>("use_gps")?.setOnPreferenceClickListener {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            true
        }
        updateLocationSummary()
    }

    private fun updateLocationSummary() {
        findPreference<Preference>("use_gps")?.summary = getString(R.string.current_location, settings.locationName)
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        val lm = requireContext().getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 6 * 3600_000L) return useLocation(last)
        val provider = providers.firstOrNull()
        if (provider == null) { last?.let { useLocation(it) } ?: toast(R.string.location_off); return }
        toast(R.string.locating)
        LocationManagerCompat.getCurrentLocation(lm, provider, CancellationSignal(), ContextCompat.getMainExecutor(requireContext())) { loc ->
            if (!isAdded) return@getCurrentLocation
            (loc ?: last)?.let { useLocation(it) } ?: toast(R.string.location_failed)
        }
    }

    private fun useLocation(loc: Location) {
        updatingLocation = true
        settings.setLocation(Math.round(loc.latitude * 10000) / 10000.0, Math.round(loc.longitude * 10000) / 10000.0,
            getString(R.string.my_location), ZoneId.systemDefault().id)
        updatingLocation = false
        updateLocationSummary()
        Reminders.scheduleAll(requireContext())
        toast(R.string.location_saved)
    }

    private fun toast(res: Int) = Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show()

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
        if (key == Settings.K_CITY && !updatingLocation) {
            prefs.getString(key, null)?.toIntOrNull()?.let { CITIES.getOrNull(it) }?.let { c ->
                updatingLocation = true
                settings.setLocation(c.lat, c.lng, c.name, c.tz)
                findPreference<ListPreference>(Settings.K_METHOD)?.value = c.method
                updatingLocation = false
                updateLocationSummary()
            }
        }
        Reminders.scheduleAll(requireContext())
    }
}
