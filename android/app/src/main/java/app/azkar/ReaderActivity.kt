package app.azkar

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** قراءة الأذكار: ذكر في كل صفحة، والمسبحة تعدّ ما بقي منه */
class ReaderActivity : BaseActivity() {
    private lateinit var kind: AzkarKind
    private lateinit var list: List<Dhikr>
    private lateinit var progress: Progress
    private lateinit var settings: Settings
    private lateinit var pager: ViewPager2
    private lateinit var tasbih: TasbihView
    private val advance = Runnable { if (pager.currentItem < list.lastIndex) pager.currentItem = pager.currentItem + 1 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)
        settings = Settings(this)
        kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { AzkarKind.valueOf(it) }.getOrNull() }
            ?: AzkarTimes.suggested(java.time.ZonedDateTime.now())
        list = Azkar.of(this, kind)
        progress = Progress(this, kind)

        findViewById<TextView>(R.id.title).text = kind.title
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.info).setOnClickListener { showInfo() }
        findViewById<View>(R.id.share).setOnClickListener { share() }
        findViewById<View>(R.id.reset).setOnClickListener { progress.reset(pager.currentItem); updateCounter(false) }
        findViewById<ImageButton>(R.id.next).setOnClickListener { if (pager.currentItem < list.lastIndex) pager.currentItem += 1 }
        findViewById<ImageButton>(R.id.prev).setOnClickListener { if (pager.currentItem > 0) pager.currentItem -= 1 }

        tasbih = findViewById(R.id.tasbih)
        tasbih.setOnClickListener { count() }

        pager = findViewById(R.id.pager)
        pager.adapter = PageAdapter()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pager.removeCallbacks(advance)
                updateHeader(position)
                updateCounter(false)
            }
        })
        val start = savedInstanceState?.getInt("page") ?: progress.firstUnfinished
        pager.setCurrentItem(start, false)
        updateHeader(start)
        updateCounter(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", pager.currentItem)
    }

    override fun onPause() {
        pager.removeCallbacks(advance)
        super.onPause()
    }

    private fun count() {
        val i = pager.currentItem
        if (progress.remaining[i] == 0) return
        progress.remaining[i]--
        progress.save()
        val finished = progress.remaining[i] == 0
        if (settings.vibrate) {
            tasbih.performHapticFeedback(
                if (finished && Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP,
            )
        }
        updateCounter(true)
        if (finished) {
            if (progress.done == progress.total) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.all_done_title, kind.title))
                    .setMessage(R.string.all_done_body)
                    .setPositiveButton(R.string.close) { _, _ -> finish() }
                    .setNegativeButton(R.string.stay, null)
                    .show()
            } else if (settings.autoAdvance) {
                pager.postDelayed(advance, 450)
            }
        }
    }

    private fun updateHeader(position: Int) {
        val current = (position + 1).toString()
        val sb = SpannableStringBuilder(current).apply {
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, current.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(ContextCompat.getColor(this@ReaderActivity, R.color.accent)), 0, current.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" / ${list.size}")
        }
        findViewById<TextView>(R.id.position).text = sb
        findViewById<View>(R.id.next).isEnabled = position < list.lastIndex
        findViewById<View>(R.id.prev).isEnabled = position > 0
        findViewById<View>(R.id.next).alpha = if (position < list.lastIndex) 1f else 0.35f
        findViewById<View>(R.id.prev).alpha = if (position > 0) 1f else 0.35f
    }

    private fun updateCounter(animate: Boolean) {
        val i = pager.currentItem
        val left = progress.remaining[i]
        tasbih.set(left, list[i].count - left, animate)
    }

    private fun showInfo() {
        val d = list[pager.currentItem]
        val body = buildString {
            if (d.fadl.isNotBlank()) append(d.fadl)
            if (d.source.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(getString(R.string.source_label)).append(' ').append(d.source)
            }
            if (isEmpty()) append(getString(R.string.no_info))
        }
        val view = layoutInflater.inflate(R.layout.dialog_info, null)
        view.findViewById<TextView>(R.id.info_body).text = body
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.info_title)
            .setView(view)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun share() {
        val d = list[pager.currentItem]
        val text = buildString {
            append(d.text)
            if (d.countText.isNotBlank()) append("\n(").append(d.countText).append(")")
            append("\n\n").append(kind.title).append(" - ").append(getString(R.string.app_name))
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.Holder>() {
        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(R.id.dhikr_text)
            val count: TextView = v.findViewById(R.id.dhikr_count)
        }

        override fun getItemCount() = list.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_page, parent, false)
            // الضغط على النص يعدّ أيضاً، كالمسبحة
            v.findViewById<View>(R.id.page_content).setOnClickListener { count() }
            return Holder(v)
        }

        override fun onBindViewHolder(h: Holder, position: Int) {
            val d = list[position]
            h.text.text = d.text
            h.text.setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.textSizeSp)
            h.count.text = d.countText
            h.count.visibility = if (d.countText.isBlank()) View.GONE else View.VISIBLE
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        fun intent(ctx: Context, kind: AzkarKind) = Intent(ctx, ReaderActivity::class.java).putExtra(EXTRA_KIND, kind.name)
    }
}
