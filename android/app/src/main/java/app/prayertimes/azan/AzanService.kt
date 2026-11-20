package app.prayertimes.azan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/** خدمة في المقدمة تشغّل الأذان حتى لو كان التطبيق مغلقاً، مع زر إيقاف في الإشعار */
class AzanService : Service() {
    private lateinit var player: AzanPlayer

    override fun onCreate() {
        super.onCreate()
        player = AzanPlayer(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAzan()
            return START_NOT_STICKY
        }
        val prayer = Prayer.fromKey(intent?.getStringExtra(EXTRA_PRAYER)) ?: Prayer.DHUHR
        val muezzin = muezzinById(AppSettings(this).muezzinFor(prayer))
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(prayer, muezzin), type)
        current = prayer
        player.play(muezzin.id, onFinished = { stopAzan() })
        return START_NOT_STICKY
    }

    private fun stopAzan() {
        player.stop()
        current = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player.stop()
        current = null
        super.onDestroy()
    }

    private fun buildNotification(prayer: Prayer, muezzin: Muezzin): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, stopIntent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.azan_now, prayer.arabic))
            .setContentText(muezzin.name)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setDeleteIntent(stop)
            .addAction(0, getString(R.string.stop_azan), stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "azan"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "app.prayertimes.azan.STOP"
        private const val EXTRA_PRAYER = "prayer"

        /** الصلاة التي يُرفع أذانها الآن (لعرض زر الإيقاف في الشاشة الرئيسية) */
        @Volatile var current: Prayer? = null
            private set

        fun startIntent(ctx: Context, prayer: Prayer) =
            Intent(ctx, AzanService::class.java).putExtra(EXTRA_PRAYER, prayer.key)

        fun stopIntent(ctx: Context) = Intent(ctx, AzanService::class.java).setAction(ACTION_STOP)

        fun createChannel(ctx: Context) {
            val channel = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.channel_desc)
                setSound(null, null) // الصوت يشغّله التطبيق نفسه
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
