package com.v2ray.ang

import android.content.Context
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.handler.SettingsManager

class AngApplication : MultiDexApplication() {
    companion object {
        lateinit var application: AngApplication
    }

    /**
     * Attaches the base context to the application.
     * @param base The base context.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        application = this
    }

    private val workManagerConfiguration: Configuration = Configuration.Builder()
        .setDefaultProcessName("${ANG_PACKAGE}:bg")
        .build()

    /**
     * Initializes the application.
     */
    override fun onCreate() {
        super.onCreate()

        MMKV.initialize(this)

        // Ensure critical preference defaults are present in MMKV early
        SettingsManager.ensureDefaultSettings()
        SettingsManager.setNightMode()
        // Initialize WorkManager with the custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        SettingsManager.initRoutingRulesets(this)

        es.dmoral.toasty.Toasty.Config.getInstance()
            .setGravity(android.view.Gravity.BOTTOM, 0, 200)
            .apply()
			
		// === افزودن این بخش برای V2Plus ===
		// مقداردهی اولیه فایربیس (حیاتی برای نوتیفیکیشن)
		// نکته: اگر از google-services plugin استفاده کرده باشید، معمولا خودکار است
		// اما اضافه کردن صریح آن اطمینان بخش است.
		try {
			com.google.firebase.FirebaseApp.initializeApp(this)
		} catch (e: Exception) {
			e.printStackTrace()
		}
		
		// اگر می‌خواهید به محض باز شدن برنامه وضعیت نوتیفیکیشن را چک کنید یا تاپیک خاصی را سابسکرایب کنید:
		com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("all_users")
		// ===================================	
    }
}
