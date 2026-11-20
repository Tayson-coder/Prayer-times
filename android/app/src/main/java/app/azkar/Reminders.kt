package app.azkar

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

/** تذكير أذكار الصباح والمساء في وقت يختاره المستخدم، وإشعارات ذكر الله على مدار اليوم */
object Reminders {
    const val ACTION_REMIND = "app.azkar.REMIND"
    const val ACTION_DHIKR = "app.azkar.DHIKR"
    const val ACTION_STOP_REMIND = "app.azkar.STOP_REMIND"
    const val ACTION_STOP_DHIKR = "app.azkar.STOP_DHIKR"
    const val EXTRA_KIND = "kind"
    const val CHANNEL_REMIND = "remind"
    const val CHANNEL_DHIKR = "dhikr"
    const val ID_DHIKR = 3000

    private fun code(kind: AzkarKind) = if (kind == AzkarKind.MORNING) 1 else 2
    fun notificationId(kind: AzkarKind) = 2000 + code(kind)

    private fun pending(ctx: Context, action: String, code: Int, kind: AzkarKind? = null): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, ReminderReceiver::class.java).setAction(action).apply { kind?.let { putExtra(EXTRA_KIND, it.name) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun scheduleAll(ctx: Context) {
        AzkarKind.entries.forEach { scheduleReminder(ctx, it) }
        scheduleDhikr(ctx)
    }

    fun scheduleReminder(ctx: Context, kind: AzkarKind, after: Instant = Instant.now()) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val s = Settings(ctx)
        val pi = pending(ctx, ACTION_REMIND, code(kind), kind)
        if (!s.reminderOn(kind)) { am.cancel(pi); return }
        val at = AzkarTimes.nextDaily(after, s.zone, s.reminderTime(kind))
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
    }

    fun scheduleDhikr(ctx: Context, after: Instant = Instant.now()) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val s = Settings(ctx)
        val pi = pending(ctx, ACTION_DHIKR, 10)
        if (!s.dhikrNotifications) { am.cancel(pi); return }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, AzkarTimes.nextDhikr(after, s.zone, s.dhikrInterval).toEpochMilli(), pi)
    }

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMIND, ctx.getString(R.string.channel_remind), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = ctx.getString(R.string.channel_remind_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DHIKR, ctx.getString(R.string.channel_dhikr), NotificationManager.IMPORTANCE_LOW)
                .apply { description = ctx.getString(R.string.channel_dhikr_desc) },
        )
    }

    fun stopIntent(ctx: Context, action: String, code: Int, kind: AzkarKind? = null) = pending(ctx, action, code, kind)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val s = Settings(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        val kind = intent.getStringExtra(Reminders.EXTRA_KIND)?.let { runCatching { AzkarKind.valueOf(it) }.getOrNull() }
        when (intent.action) {
            Reminders.ACTION_REMIND -> if (kind != null) {
                if (s.reminderOn(kind)) notifyReminder(ctx, kind)
                Reminders.scheduleReminder(ctx, kind, Instant.now().plusSeconds(60))
            }
            Reminders.ACTION_DHIKR -> {
                if (s.dhikrNotifications) notifyDhikr(ctx)
                Reminders.scheduleDhikr(ctx)
            }
            Reminders.ACTION_STOP_REMIND -> if (kind != null) {
                s.setReminder(kind, false)
                nm.cancel(Reminders.notificationId(kind))
                Reminders.scheduleReminder(ctx, kind)
            }
            Reminders.ACTION_STOP_DHIKR -> {
                s.prefs.edit().putBoolean(Settings.K_DHIKR, false).apply()
                nm.cancel(Reminders.ID_DHIKR)
                Reminders.scheduleDhikr(ctx)
            }
            // BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED / MY_PACKAGE_REPLACED
            else -> Reminders.scheduleAll(ctx)
        }
    }

    private fun notifyReminder(ctx: Context, kind: AzkarKind) {
        val open = PendingIntent.getActivity(
            ctx, 20 + kind.ordinal, ReaderActivity.intent(ctx, kind).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, Reminders.CHANNEL_REMIND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(kind.title)
            .setContentText(ctx.getString(if (kind == AzkarKind.MORNING) R.string.remind_morning_body else R.string.remind_evening_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, ctx.getString(R.string.read_now), open)
            .addAction(0, ctx.getString(R.string.stop_reminder), Reminders.stopIntent(ctx, Reminders.ACTION_STOP_REMIND, 30 + kind.ordinal, kind))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(Reminders.notificationId(kind), n) }
    }

    private fun notifyDhikr(ctx: Context) {
        val text = AzkarData.short(Azkar.all(ctx)).random()
        val open = PendingIntent.getActivity(
            ctx, 40, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, Reminders.CHANNEL_DHIKR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(true)
            .addAction(0, ctx.getString(R.string.stop_dhikr), Reminders.stopIntent(ctx, Reminders.ACTION_STOP_DHIKR, 41))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(Reminders.ID_DHIKR, n) }
    }
}
