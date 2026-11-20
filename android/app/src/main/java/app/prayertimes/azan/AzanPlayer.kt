package app.prayertimes.azan

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/** مصادر صوت الأذان لكل مؤذن، بالترتيب: ملف مختار ← رابط ← ملف مدمج ← نغمة المنبّه */
object AzanAudio {
    sealed class Source(val label: String) {
        class FilePath(val file: File) : Source("ملف مختار")
        class Url(val url: String) : Source("رابط")
        class Raw(val resId: Int) : Source("مدمج في التطبيق")
        class Fallback(val uri: Uri) : Source("نغمة المنبّه (بديل)")
    }

    fun userFile(ctx: Context, id: String) = File(File(ctx.filesDir, "azan"), id)

    @SuppressLint("DiscouragedApi")
    fun rawId(ctx: Context, id: String): Int = ctx.resources.getIdentifier(id, "raw", ctx.packageName)

    fun sources(ctx: Context, id: String, includeFallback: Boolean = true): List<Source> = buildList {
        userFile(ctx, id).takeIf { it.exists() && it.length() > 0 }?.let { add(Source.FilePath(it)) }
        AppSettings(ctx).customUrl(id)?.let { add(Source.Url(it)) }
        rawId(ctx, id).takeIf { it != 0 }?.let { add(Source.Raw(it)) }
        if (includeFallback) {
            (RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                ?.let { add(Source.Fallback(it)) }
        }
    }

    /** وصف مصدر الصوت المتاح، أو null إذا لم يوجد إلا البديل */
    fun describe(ctx: Context, id: String): String? = sources(ctx, id, includeFallback = false).firstOrNull()?.label
}

/** يشغّل الأذان مع الانتقال تلقائياً للمصدر التالي عند الفشل */
class AzanPlayer(private val ctx: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var queue = ArrayDeque<AzanAudio.Source>()
    private var onFinished: (() -> Unit)? = null
    private var onStarted: ((AzanAudio.Source) -> Unit)? = null
    private val timeout = Runnable { Log.w(TAG, "source timed out"); next() }
    private val fallbackLimit = Runnable { finish() }

    val isPlaying: Boolean get() = player != null

    fun play(muezzinId: String, onStarted: (AzanAudio.Source) -> Unit = {}, onFinished: () -> Unit = {}) {
        stop()
        this.onStarted = onStarted
        this.onFinished = onFinished
        queue = ArrayDeque(AzanAudio.sources(ctx, muezzinId))
        next()
    }

    private fun next() {
        release()
        val src = queue.removeFirstOrNull() ?: return finish()
        val volume = AppSettings(ctx).volume
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            when (src) {
                is AzanAudio.Source.FilePath -> mp.setDataSource(src.file.absolutePath)
                is AzanAudio.Source.Url -> mp.setDataSource(src.url)
                is AzanAudio.Source.Raw -> ctx.resources.openRawResourceFd(src.resId).use { mp.setDataSource(it) }
                is AzanAudio.Source.Fallback -> mp.setDataSource(ctx, src.uri)
            }
            mp.setOnPreparedListener {
                handler.removeCallbacks(timeout)
                it.setVolume(volume, volume)
                it.start()
                onStarted?.invoke(src)
                // نغمة المنبّه قد تكون طويلة أو متكررة: نوقفها بعد 30 ثانية
                if (src is AzanAudio.Source.Fallback) handler.postDelayed(fallbackLimit, 30_000)
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "error $what/$extra for ${src.label}")
                handler.post { next() }
                true
            }
            mp.setOnCompletionListener { finish() }
            handler.postDelayed(timeout, 20_000)
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "cannot play ${src.label}", e)
            handler.post { next() }
        }
    }

    private fun finish() {
        release()
        queue.clear()
        val cb = onFinished
        onFinished = null
        cb?.invoke()
    }

    private fun release() {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(fallbackLimit)
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }

    fun stop() {
        onFinished = null
        queue.clear()
        release()
    }

    companion object { private const val TAG = "AzanPlayer" }
}
