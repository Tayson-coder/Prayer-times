package app.prayertimes.azan

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Instant

/** جدولة تذكير أذكار الصباح والمساء، وإشعارات الذكر على مدار اليوم */
object DhikrScheduler {
    const val ACTION_AZKAR = "app.prayertimes.azan.ACTION_AZKAR"
    const val ACTION_DHIKR = "app.prayertimes.azan.ACTION_DHIKR"
    const val ACTION_DISABLE_DHIKR = "app.prayertimes.azan.ACTION_DISABLE_DHIKR"
    const val ACTION_DISABLE_AZKAR = "app.prayertimes.azan.ACTION_DISABLE_AZKAR"
    const val EXTRA_KIND = "kind"

    const val CHANNEL_AZKAR = "azkar"
    const val CHANNEL_DHIKR = "dhikr"
    const val ID_AZKAR = 2001
    const val ID_DHIKR = 2002

    fun schedule(ctx: Context, after: Instant = Instant.now()) {
        scheduleAzkar(ctx, after)
        scheduleDhikr(ctx, after)
    }

    private fun pending(ctx: Context, action: String, code: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, DhikrReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun scheduleAzkar(ctx: Context, after: Instant = Instant.now()) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val s = AppSettings(ctx)
        if (!s.azkarReminders) {
            am.cancel(pending(ctx, ACTION_AZKAR, 10))
            return
        }
        val (kind, at) = AzkarTimes.nextReminder(s.location(), after)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending(ctx, ACTION_AZKAR, 10) { putExtra(EXTRA_KIND, kind.name) })
    }

    fun scheduleDhikr(ctx: Context, after: Instant = Instant.now()) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val s = AppSettings(ctx)
        if (!s.dhikrNotifications) {
            am.cancel(pending(ctx, ACTION_DHIKR, 11))
            return
        }
        val at = AzkarTimes.nextDhikr(after, s.zone, s.dhikrIntervalHours)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending(ctx, ACTION_DHIKR, 11))
    }

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_AZKAR, ctx.getString(R.string.channel_azkar), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = ctx.getString(R.string.channel_azkar_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DHIKR, ctx.getString(R.string.channel_dhikr), NotificationManager.IMPORTANCE_LOW)
                .apply { description = ctx.getString(R.string.channel_dhikr_desc) },
        )
    }
}

class DhikrReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val s = AppSettings(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        when (intent.action) {
            DhikrScheduler.ACTION_AZKAR -> {
                val kind = runCatching { AzkarKind.valueOf(intent.getStringExtra(DhikrScheduler.EXTRA_KIND) ?: "") }
                    .getOrDefault(AzkarKind.MORNING)
                if (s.azkarReminders) notifyAzkar(ctx, kind)
                DhikrScheduler.scheduleAzkar(ctx, Instant.now().plusSeconds(60))
            }
            DhikrScheduler.ACTION_DHIKR -> {
                if (s.dhikrNotifications) notifyDhikr(ctx)
                DhikrScheduler.scheduleDhikr(ctx)
            }
            DhikrScheduler.ACTION_DISABLE_DHIKR -> {
                s.prefs.edit().putBoolean(AppSettings.KEY_DHIKR_NOTIFICATIONS, false).apply()
                nm.cancel(DhikrScheduler.ID_DHIKR)
                DhikrScheduler.scheduleDhikr(ctx)
            }
            DhikrScheduler.ACTION_DISABLE_AZKAR -> {
                s.prefs.edit().putBoolean(AppSettings.KEY_AZKAR_REMINDERS, false).apply()
                nm.cancel(DhikrScheduler.ID_AZKAR)
                DhikrScheduler.scheduleAzkar(ctx)
            }
        }
    }

    private fun broadcast(ctx: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, DhikrReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notifyAzkar(ctx: Context, kind: AzkarKind) {
        val open = PendingIntent.getActivity(
            ctx, 20, AzkarActivity.intent(ctx, kind).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, DhikrScheduler.CHANNEL_AZKAR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(kind.title)
            .setContentText(ctx.getString(if (kind == AzkarKind.MORNING) R.string.azkar_morning_body else R.string.azkar_evening_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, ctx.getString(R.string.read_now), open)
            .addAction(0, ctx.getString(R.string.turn_off_reminders), broadcast(ctx, DhikrScheduler.ACTION_DISABLE_AZKAR, 21))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(DhikrScheduler.ID_AZKAR, n) }
    }

    private fun notifyDhikr(ctx: Context) {
        val text = SHORT_DHIKR.random()
        val open = PendingIntent.getActivity(
            ctx, 22, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, DhikrScheduler.CHANNEL_DHIKR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(true)
            .addAction(0, ctx.getString(R.string.turn_off_dhikr), broadcast(ctx, DhikrScheduler.ACTION_DISABLE_DHIKR, 23))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(DhikrScheduler.ID_DHIKR, n) }
    }
}
