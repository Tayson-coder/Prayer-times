package app.prayertimes.azan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Settings
import android.text.InputType
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.time.ZoneId
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
        if (intent.getBooleanExtra(EXTRA_FIRST_RUN, false)) {
            Toast.makeText(this, R.string.choose_location_first, Toast.LENGTH_LONG).show()
        }
    }

    companion object { const val EXTRA_FIRST_RUN = "first_run" }
}

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var settings: AppSettings
    private var updatingLocation = false

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) fetchLocation() else toast(R.string.location_denied)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        settings = AppSettings(requireContext())

        findPreference<ListPreference>(AppSettings.KEY_CITY)?.apply {
            entries = CITIES.map { it.name }.toTypedArray()
            entryValues = CITIES.indices.map { it.toString() }.toTypedArray()
        }
        findPreference<ListPreference>(AppSettings.KEY_METHOD)?.apply {
            entries = PrayTimes.METHODS.values.map { it.name }.toTypedArray()
            entryValues = PrayTimes.METHODS.keys.toTypedArray()
        }
        val names = MUEZZINS.map { it.name }.toTypedArray()
        val ids = MUEZZINS.map { it.id }.toTypedArray()
        findPreference<ListPreference>(AppSettings.KEY_MUEZZIN)?.apply { entries = names; entryValues = ids }
        findPreference<ListPreference>(AppSettings.KEY_FAJR_MUEZZIN)?.apply {
            entries = arrayOf(getString(R.string.same_muezzin)) + names
            entryValues = arrayOf(AppSettings.SAME) + ids
        }

        val signedNumber = EditTextPreference.OnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
            it.setSelection(it.text.length)
        }
        listOf(AppSettings.KEY_LAT, AppSettings.KEY_LNG).forEach { findPreference<EditTextPreference>(it)?.setOnBindEditTextListener(signedNumber) }
        Prayer.entries.forEach { findPreference<EditTextPreference>("adjust_${it.key}")?.setOnBindEditTextListener(signedNumber) }

        findPreference<Preference>("use_gps")?.setOnPreferenceClickListener {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            true
        }
        findPreference<Preference>("open_azkar")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AzkarActivity::class.java)); true
        }
        findPreference<Preference>("manage_audio")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AudioActivity::class.java)); true
        }
        findPreference<Preference>("exact_alarm")?.apply {
            isVisible = Build.VERSION.SDK_INT >= 31
            setOnPreferenceClickListener {
                if (Build.VERSION.SDK_INT >= 31) {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${requireContext().packageName}")))
                }
                true
            }
        }
        findPreference<Preference>("battery")?.setOnPreferenceClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); true
        }
        findPreference<Preference>("test_azan")?.setOnPreferenceClickListener {
            ContextCompat.startForegroundService(requireContext(), AzanService.startIntent(requireContext(), Prayer.DHUHR))
            true
        }
        updateLocationSummary()
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        findPreference<Preference>("exact_alarm")?.summary = getString(
            if (AzanScheduler.canScheduleExact(requireContext())) R.string.exact_ok else R.string.exact_missing,
        )
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (!updatingLocation) {
            when (key) {
                AppSettings.KEY_CITY -> prefs.getString(key, null)?.toIntOrNull()?.let { CITIES.getOrNull(it) }?.let { c ->
                    applyLocation(c.lat, c.lng, c.name, c.tz)
                    findPreference<ListPreference>(AppSettings.KEY_METHOD)?.value = c.method
                }
                AppSettings.KEY_LAT, AppSettings.KEY_LNG -> {
                    val lat = prefs.getString(AppSettings.KEY_LAT, null)?.toDoubleOrNull()
                    val lng = prefs.getString(AppSettings.KEY_LNG, null)?.toDoubleOrNull()
                    if (lat != null && lng != null && kotlin.math.abs(lat) <= 90 && kotlin.math.abs(lng) <= 180) {
                        applyLocation(lat, lng, String.format(Locale.US, "%.3f، %.3f", lat, lng), ZoneId.systemDefault().id)
                    } else {
                        toast(R.string.bad_coords)
                    }
                }
            }
        }
        AzanScheduler.schedule(requireContext())
        DhikrScheduler.schedule(requireContext())
    }

    private fun applyLocation(lat: Double, lng: Double, name: String, tz: String) {
        updatingLocation = true
        settings.setLocation(lat, lng, name, tz)
        findPreference<EditTextPreference>(AppSettings.KEY_LAT)?.text = lat.toString()
        findPreference<EditTextPreference>(AppSettings.KEY_LNG)?.text = lng.toString()
        updatingLocation = false
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
        if (provider == null) {
            last?.let { useLocation(it) } ?: toast(R.string.location_off)
            return
        }
        toast(R.string.locating)
        LocationManagerCompat.getCurrentLocation(lm, provider, CancellationSignal(), ContextCompat.getMainExecutor(requireContext())) { loc ->
            if (!isAdded) return@getCurrentLocation
            (loc ?: last)?.let { useLocation(it) } ?: toast(R.string.location_failed)
        }
    }

    private fun useLocation(loc: Location) {
        val lat = Math.round(loc.latitude * 10000) / 10000.0
        val lng = Math.round(loc.longitude * 10000) / 10000.0
        applyLocation(lat, lng, getString(R.string.my_location), ZoneId.systemDefault().id)
        AzanScheduler.schedule(requireContext())
        toast(R.string.location_saved)
    }

    private fun toast(res: Int) = Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show()
}
