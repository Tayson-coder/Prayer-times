package app.prayertimes.azan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.Instant

/** جدولة منبّه دقيق للأذان القادم فقط؛ وعند رنّه يُجدوَل الذي بعده */
object AzanScheduler {
    const val ACTION_AZAN = "app.prayertimes.azan.ACTION_AZAN"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_AT = "at"
    private const val TAG = "AzanScheduler"

    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun schedule(ctx: Context, after: Instant = Instant.now()) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val intent = Intent(ctx, AzanReceiver::class.java).setAction(ACTION_AZAN)
        val settings = AppSettings(ctx)
        val next = PrayerSchedule.nextAzan(settings.location(), after) { settings.isEnabled(it) }
        if (next == null) {
            am.cancel(PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return
        }
        val (prayer, at) = next
        intent.putExtra(EXTRA_PRAYER, prayer.key).putExtra(EXTRA_AT, at.toEpochMilli())
        val pi = PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (canScheduleExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
            }
            Log.i(TAG, "scheduled ${prayer.key} at $at")
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        }
    }
}

/** يستقبل منبّه الأذان، وإعادة التشغيل، وتغيير الوقت/المنطقة الزمنية */
class AzanReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == AzanScheduler.ACTION_AZAN) {
            val prayer = Prayer.fromKey(intent.getStringExtra(AzanScheduler.EXTRA_PRAYER))
            val at = intent.getLongExtra(AzanScheduler.EXTRA_AT, 0L)
            val now = System.currentTimeMillis()
            // لا نرفع الأذان إذا تأخر المنبّه كثيراً (مثلاً الجهاز كان مغلقاً)
            if (prayer != null && AppSettings(ctx).isEnabled(prayer) && now - at in -60_000L..600_000L) {
                ContextCompat.startForegroundService(ctx, AzanService.startIntent(ctx, prayer))
            }
            AzanScheduler.schedule(ctx, Instant.ofEpochMilli(maxOf(now, at) + 1000))
        } else {
            // BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED / MY_PACKAGE_REPLACED
            AzanScheduler.schedule(ctx)
        }
    }
}
