package app.prayertimes.azan

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
        class Online(val url: String) : Source("من الإنترنت")
        class Fallback(val uri: Uri) : Source("نغمة المنبّه (بديل)")
    }

    fun userFile(ctx: Context, id: String) = File(File(ctx.filesDir, "azan"), id)

    fun isDownloadable(ctx: Context, id: String): Boolean =
        muezzinById(id).let { it.id == id && it.url != null } && describe(ctx, id) == null

    @SuppressLint("DiscouragedApi")
    fun rawId(ctx: Context, id: String): Int = ctx.resources.getIdentifier(id, "raw", ctx.packageName)

    fun sources(ctx: Context, id: String, includeFallback: Boolean = true): List<Source> = buildList {
        userFile(ctx, id).takeIf { it.exists() && it.length() > 0 }?.let { add(Source.FilePath(it)) }
        AppSettings(ctx).customUrl(id)?.let { add(Source.Url(it)) }
        rawId(ctx, id).takeIf { it != 0 }?.let { add(Source.Raw(it)) }
        if (includeFallback) {
            // لم يُحمَّل الملف بعد: نجرب تشغيله مباشرة من الإنترنت قبل نغمة المنبّه
            muezzinById(id).url?.takeIf { muezzinById(id).id == id }?.let { add(Source.Online(it)) }
            (RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                ?.let { add(Source.Fallback(it)) }
        }
    }

    /** وصف مصدر الصوت المتاح، أو null إذا لم يوجد إلا البديل */
    fun describe(ctx: Context, id: String): String? = sources(ctx, id, includeFallback = false).firstOrNull()?.label
}

/**
 * يشغّل الأذان مع الانتقال تلقائياً للمصدر التالي عند الفشل.
 * الصوت يتبع «صوت الوسائط» في الموبايل (وأزرار الصوت) إلا إذا اختار المستخدم صوت المنبّه،
 * ومستوى الصوت في الإعدادات نسبة من صوت الموبايل ويتغير فوراً أثناء التشغيل.
 */
class AzanPlayer(private val ctx: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val settings = AppSettings(ctx)
    private val audioManager = ctx.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var queue = ArrayDeque<AzanAudio.Source>()
    private var onFinished: (() -> Unit)? = null
    private var onStarted: ((AzanAudio.Source) -> Unit)? = null
    private var focusRequest: AudioFocusRequest? = null
    private val timeout = Runnable { Log.w(TAG, "source timed out"); next() }
    private val fallbackLimit = Runnable { finish() }

    // تغيير مستوى الصوت من الإعدادات يُطبَّق فوراً على الأذان الجاري
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppSettings.KEY_VOLUME) applyVolume()
    }

    val isPlaying: Boolean get() = player != null

    fun play(muezzinId: String, onStarted: (AzanAudio.Source) -> Unit = {}, onFinished: () -> Unit = {}) {
        stop()
        this.onStarted = onStarted
        this.onFinished = onFinished
        queue = ArrayDeque(AzanAudio.sources(ctx, muezzinId))
        settings.prefs.registerOnSharedPreferenceChangeListener(prefListener)
        requestFocus()
        next()
    }

    private fun attributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(if (settings.useAlarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** يوقف الموسيقى/الفيديو مؤقتاً أثناء الأذان ثم يعيدها */
    private fun requestFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes())
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = req
        runCatching { audioManager.requestAudioFocus(req) }
    }

    private fun abandonFocus() {
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    /** منحنى تربيعي حتى يكون تخفيض الصوت محسوساً بشكل طبيعي */
    private fun applyVolume() {
        val v = settings.volume.coerceIn(0f, 1f)
        val scaled = v * v
        runCatching { player?.setVolume(scaled, scaled) }
    }

    private fun next() {
        release()
        val src = queue.removeFirstOrNull() ?: return finish()
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(attributes())
            when (src) {
                is AzanAudio.Source.FilePath -> mp.setDataSource(src.file.absolutePath)
                is AzanAudio.Source.Url -> mp.setDataSource(src.url)
                is AzanAudio.Source.Online -> mp.setDataSource(src.url)
                is AzanAudio.Source.Raw -> ctx.resources.openRawResourceFd(src.resId).use { mp.setDataSource(it) }
                is AzanAudio.Source.Fallback -> mp.setDataSource(ctx, src.uri)
            }
            mp.setOnPreparedListener {
                handler.removeCallbacks(timeout)
                applyVolume()
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
        cleanup()
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

    private fun cleanup() {
        release()
        queue.clear()
        abandonFocus()
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    fun stop() {
        onFinished = null
        cleanup()
    }

    companion object { private const val TAG = "AzanPlayer" }
}
