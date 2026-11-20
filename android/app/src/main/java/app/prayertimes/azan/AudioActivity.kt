package app.prayertimes.azan

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import kotlin.concurrent.thread

/** إدارة أصوات المؤذنين: اختيار ملف من الجهاز، أو رابط مباشر، أو الاستماع والحذف */
class AudioActivity : BaseActivity() {
    private lateinit var settings: AppSettings
    private lateinit var player: AzanPlayer
    private var pendingId: String? = null

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
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pending", pendingId)
    }

    override fun onStop() {
        player.stop()
        super.onStop()
    }

    private fun render() {
        val list = findViewById<LinearLayout>(R.id.audio_list)
        list.removeAllViews()
        for (m in MUEZZINS) {
            val row = layoutInflater.inflate(R.layout.item_muezzin_audio, list, false)
            row.findViewById<TextView>(R.id.m_name).text = m.name
            row.findViewById<TextView>(R.id.m_note).text = m.note
            val label = AzanAudio.describe(this, m.id)
            row.findViewById<TextView>(R.id.m_status).apply {
                text = label ?: getString(R.string.no_audio)
                isActivated = label != null
            }
            row.findViewById<Button>(R.id.m_pick).setOnClickListener {
                pendingId = m.id
                pickFile.launch(arrayOf("audio/*"))
            }
            row.findViewById<Button>(R.id.m_url).setOnClickListener { askUrl(m) }
            row.findViewById<Button>(R.id.m_play).setOnClickListener { b ->
                b as Button
                if (player.isPlaying) {
                    player.stop(); b.setText(R.string.listen)
                } else {
                    b.setText(R.string.stop)
                    player.play(m.id, onFinished = { b.setText(R.string.listen) })
                }
            }
            val hasFile = AzanAudio.userFile(this, m.id).exists()
            row.findViewById<Button>(R.id.m_delete).apply {
                visibility = if (hasFile || settings.customUrl(m.id) != null) Button.VISIBLE else Button.GONE
                setOnClickListener {
                    AzanAudio.userFile(this@AudioActivity, m.id).delete()
                    settings.setCustomUrl(m.id, null)
                    render()
                }
            }
            list.addView(row)
        }
    }

    private fun askUrl(m: Muezzin) {
        val input = EditText(this).apply {
            hint = getString(R.string.url_hint)
            setText(settings.customUrl(m.id) ?: "")
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        AlertDialog.Builder(this)
            .setTitle(m.name)
            .setView(input)
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
