package app.prayertimes.azan

import android.app.Application
import android.media.AudioManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        AzanService.createChannel(this)
        AzanScheduler.schedule(this)
    }
}

/** كل الشاشات من اليمين لليسار بغض النظر عن لغة الجهاز */
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        // أزرار الصوت داخل التطبيق تتحكم في صوت الوسائط (صوت الأذان)
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
