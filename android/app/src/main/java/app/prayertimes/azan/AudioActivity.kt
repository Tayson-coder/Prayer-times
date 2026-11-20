package app.prayertimes.azan

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.concurrent.thread

/** أصوات المؤذنين: استماع، وتغيير الصوت بملف من الجهاز أو رابط، وتحميل الناقص */
class AudioActivity : BaseActivity() {
    private lateinit var settings: AppSettings
    private lateinit var player: AzanPlayer
    private var pendingId: String? = null
    private var playingId: String? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val id = pendingId ?: return@registerForActivityResult
        if (uri != null) importFile(id, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        settings = AppSettings(this)
        player = AzanPlayer(this)
        pendingId = savedInstanceState?.getString("pending")
        findViewById<Button>(R.id.download_all).setOnClickListener { b ->
            b.isEnabled = false
            AzanDownloader.downloadAll(this) { done, total, failed ->
                if (total > 0) {
                    (b as Button).text = getString(R.string.downloading_n, done, total)
                    if (done == total) {
                        Toast.makeText(
                            this,
                            if (failed == 0) getString(R.string.download_all_ok) else getString(R.string.download_some_failed, failed),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                if (done == total) render()
            }
            render()
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pending", pendingId)
    }

    override fun onStop() {
        player.stop()
        playingId = null
        super.onStop()
    }

    private fun render() {
        val remaining = MUEZZINS.count { AzanAudio.isDownloadable(this, it.id) }
        findViewById<Button>(R.id.download_all).apply {
            if (MUEZZINS.none { AzanDownloader.isDownloading(it.id) }) {
                visibility = if (remaining > 0) View.VISIBLE else View.GONE
                isEnabled = true
                text = getString(R.string.download_all, remaining)
            }
        }
        val list = findViewById<LinearLayout>(R.id.audio_list)
        list.removeAllViews()
        for (m in MUEZZINS) list.addView(row(list, m))
    }

    private fun row(parent: LinearLayout, m: Muezzin): View {
        val row = layoutInflater.inflate(R.layout.item_muezzin_audio, parent, false) as FrameLayout
        row.findViewById<TextView>(R.id.m_name).text = m.name
        row.findViewById<TextView>(R.id.m_note).text = m.note

        val source = AzanAudio.sources(this, m.id, includeFallback = false).firstOrNull()
        val downloading = AzanDownloader.isDownloading(m.id)
        // الصوت المدمج هو الحالة العادية فلا نكتب عنه شيئاً؛ نعرض الحالة فقط إذا اختلفت
        val status = when {
            downloading -> getString(R.string.downloading)
            source is AzanAudio.Source.Raw -> null
            source != null -> source.label
            m.url != null -> getString(R.string.available_download)
            else -> getString(R.string.no_audio)
        }
        row.findViewById<TextView>(R.id.m_status).apply {
            text = status
            visibility = if (status == null) View.GONE else View.VISIBLE
            setTextColor(ContextCompat.getColor(context, if (source != null) R.color.turquoise else R.color.muted))
        }

        row.findViewById<Button>(R.id.m_download).apply {
            visibility = if (AzanAudio.isDownloadable(this@AudioActivity, m.id)) View.VISIBLE else View.GONE
            isEnabled = !downloading
            setOnClickListener {
                AzanDownloader.download(this@AudioActivity, m.id) { ok ->
                    Toast.makeText(this@AudioActivity, if (ok) R.string.file_saved else R.string.download_failed, Toast.LENGTH_SHORT).show()
                    render()
                }
                render()
            }
        }

        row.findViewById<ImageButton>(R.id.m_play).apply {
            val isThis = playingId == m.id
            setImageResource(if (isThis) R.drawable.ic_stop else R.drawable.ic_play)
            contentDescription = getString(if (isThis) R.string.stop else R.string.listen)
            setOnClickListener {
                if (playingId == m.id) {
                    player.stop()
                    playingId = null
                } else {
                    playingId = m.id
                    player.play(m.id, onFinished = { playingId = null; render() })
                }
                render()
            }
        }

        row.findViewById<ImageButton>(R.id.m_more).setOnClickListener { anchor ->
            val hasCustom = AzanAudio.userFile(this, m.id).exists() || settings.customUrl(m.id) != null
            PopupMenu(this, anchor).apply {
                menu.add(0, 1, 0, R.string.menu_pick_file)
                menu.add(0, 2, 1, R.string.menu_link)
                if (hasCustom) menu.add(0, 3, 2, R.string.menu_remove)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> { pendingId = m.id; pickFile.launch(arrayOf("audio/*")) }
                        2 -> askUrl(m)
                        3 -> {
                            AzanAudio.userFile(this@AudioActivity, m.id).delete()
                            settings.setCustomUrl(m.id, null)
                            render()
                        }
                    }
                    true
                }
                show()
            }
        }
        return row
    }

    private fun askUrl(m: Muezzin) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            hint = getString(R.string.url_hint)
            setText(settings.customUrl(m.id) ?: "")
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            textDirection = View.TEXT_DIRECTION_LTR
        }
        val box = FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(input) }
        MaterialAlertDialogBuilder(this)
            .setTitle(m.name)
            .setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty() && !url.startsWith("http")) {
                    Toast.makeText(this, R.string.bad_url, Toast.LENGTH_SHORT).show()
                } else {
                    settings.setCustomUrl(m.id, url)
                    render()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun importFile(id: String, uri: Uri) {
        val target = AzanAudio.userFile(this, id)
        thread {
            val ok = runCatching {
                target.parentFile?.mkdirs()
                val tmp = java.io.File(target.parentFile, "$id.tmp")
                contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(target)
            }.getOrDefault(false)
            runOnUiThread {
                Toast.makeText(this, if (ok) R.string.file_saved else R.string.file_failed, Toast.LENGTH_SHORT).show()
                render()
            }
        }
    }
}
