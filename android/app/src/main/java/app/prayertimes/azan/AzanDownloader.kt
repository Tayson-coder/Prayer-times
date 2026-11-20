package app.prayertimes.azan

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** تحميل أصوات المؤذنين من مصادرها العامة وحفظها داخل التطبيق لتعمل بدون إنترنت */
object AzanDownloader {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val inProgress = mutableSetOf<String>()

    fun isDownloading(id: String) = synchronized(inProgress) { id in inProgress }

    /** يحمّل صوت مؤذن واحد؛ onDone(true) عند النجاح (يُستدعى على الخيط الرئيسي) */
    fun download(ctx: Context, id: String, onDone: (Boolean) -> Unit = {}) {
        val app = ctx.applicationContext
        val url = muezzinById(id).takeIf { it.id == id }?.url
        if (url == null) { onDone(false); return }
        synchronized(inProgress) { if (!inProgress.add(id)) return }
        executor.execute {
            val ok = runCatching { fetch(url, AzanAudio.userFile(app, id)) }
                .onFailure { Log.w("AzanDownloader", "download failed for $id", it) }
                .getOrDefault(false)
            synchronized(inProgress) { inProgress.remove(id) }
            main.post { onDone(ok) }
        }
    }

    /** يحمّل كل الأصوات المتاحة غير الموجودة؛ onProgress(تم, الإجمالي, فشل) */
    fun downloadAll(ctx: Context, onProgress: (done: Int, total: Int, failed: Int) -> Unit) {
        val ids = MUEZZINS.filter { AzanAudio.isDownloadable(ctx, it.id) }.map { it.id }
        if (ids.isEmpty()) { onProgress(0, 0, 0); return }
        var done = 0
        var failed = 0
        for (id in ids) {
            download(ctx, id) { ok ->
                done++
                if (!ok) failed++
                onProgress(done, ids.size, failed)
            }
        }
    }

    private fun fetch(url: String, target: File): Boolean {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) return false
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        // ملف صوتي حقيقي وليس صفحة خطأ
        if (tmp.length() < 50_000) { tmp.delete(); return false }
        return tmp.renameTo(target)
    }
}
