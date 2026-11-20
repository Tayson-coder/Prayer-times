package app.azkar

import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/** أذكار التسبيح الحر */
val TASBIH_PHRASES = listOf(
    "سُبْحَانَ اللَّهِ",
    "الْحَمْدُ لِلَّهِ",
    "اللَّهُ أَكْبَرُ",
    "لَا إِلَهَ إِلَّا اللَّهُ",
    "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
    "أَسْتَغْفِرُ اللَّهَ",
    "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ",
    "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ",
)

/** السبحة: عدّ حر لأي ذكر، بهدف ٣٣ أو ١٠٠ أو ١٠٠٠ أو بلا حد، وأزرار الصوت تعدّ أيضاً */
class TasbihActivity : BaseActivity() {
    private val arabic = Locale("ar", "EG")
    private lateinit var settings: Settings
    private lateinit var tasbih: TasbihView
    private var phrase = 0
    private var target = 33
    private var count = 0

    private fun key(name: String) = "tasbih_${name}_$phrase"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasbih)
        settings = Settings(this)
        val prefs = settings.prefs
        phrase = prefs.getInt("tasbih_phrase", 0).coerceIn(0, TASBIH_PHRASES.lastIndex)
        target = prefs.getInt("tasbih_target", 33)

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.reset_all).setOnClickListener { confirmReset() }
        tasbih = findViewById(R.id.tasbih)
        tasbih.setOnClickListener { tap() }
        findViewById<View>(R.id.tap_area).setOnClickListener { tap() }

        val chips = findViewById<ChipGroup>(R.id.phrases)
        TASBIH_PHRASES.forEachIndexed { i, text ->
            val chip = layoutInflater.inflate(R.layout.item_phrase_chip, chips, false) as Chip
            chip.id = View.generateViewId()
            chip.text = text
            chip.isChecked = i == phrase
            chip.setOnClickListener { selectPhrase(i) }
            chips.addView(chip)
        }

        val targets = findViewById<MaterialButtonToggleGroup>(R.id.targets)
        targets.check(when (target) { 100 -> R.id.t100; 1000 -> R.id.t1000; 0 -> R.id.tinf; else -> R.id.t33 })
        targets.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            target = when (id) { R.id.t100 -> 100; R.id.t1000 -> 1000; R.id.tinf -> 0; else -> 33 }
            prefs.edit().putInt("tasbih_target", target).apply()
            render(false)
        }
        load()
    }

    private fun load() {
        count = settings.prefs.getInt(key("count"), 0)
        findViewById<TextView>(R.id.phrase).text = TASBIH_PHRASES[phrase]
        render(false)
    }

    private fun selectPhrase(i: Int) {
        phrase = i
        settings.prefs.edit().putInt("tasbih_phrase", i).apply()
        load()
    }

    private fun tap() {
        count++
        val total = settings.prefs.getLong(key("total"), 0) + 1
        settings.prefs.edit().putInt(key("count"), count).putLong(key("total"), total).apply()
        val roundDone = target > 0 && count % target == 0
        if (settings.vibrate) {
            tasbih.performHapticFeedback(
                if (roundDone && Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP,
            )
        }
        render(true)
    }

    private fun render(animate: Boolean) {
        val shown = if (target > 0 && count > 0) ((count - 1) % target) + 1 else count
        tasbih.set(shown, count, animate, done = false)
        val rounds = if (target > 0) count / target else 0
        val total = settings.prefs.getLong(key("total"), 0)
        fun n(x: Number) = String.format(arabic, "%,d", x)
        findViewById<TextView>(R.id.stats).text = buildString {
            if (target > 0) append(getString(R.string.tasbih_of_target, n(target)))
            if (rounds > 0) { if (isNotEmpty()) append("\n"); append(getString(R.string.tasbih_rounds, n(rounds))) }
            if (isNotEmpty()) append("\n")
            append(getString(R.string.tasbih_total, n(total)))
        }
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tasbih_reset_title)
            .setMessage(R.string.tasbih_reset_body)
            .setPositiveButton(R.string.tasbih_reset_yes) { _, _ ->
                count = 0
                settings.prefs.edit().putInt(key("count"), 0).apply()
                render(false)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // أزرار الصوت تعدّ في هذه الشاشة، للتسبيح والشاشة مطفأة النظر
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.repeatCount == 0) tap()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
