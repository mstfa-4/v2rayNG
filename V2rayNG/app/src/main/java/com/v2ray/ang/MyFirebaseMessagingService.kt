package com.v2ray.ang

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import android.util.Log
import com.v2ray.ang.ui.MainActivity
import java.net.URL

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "Message received: ${remoteMessage.data}")

        // استخراج عنوان و متن از notification
        val title = remoteMessage.notification?.title ?: "Notification"
        val body = remoteMessage.notification?.body ?: "New message"
        // استخراج URL تصویر و لینک کلیک از data
        val imageUrl = remoteMessage.data["imageUrl"]
        val openUrl = remoteMessage.data["openUrl"]

        sendNotification(title, body, imageUrl, openUrl)
    }

    override fun onNewToken(token: String) {
        Log.d("FCM", "New token: $token")
    }

    private fun sendNotification(title: String, messageBody: String, imageUrl: String?, openUrl: String?) {
        val channelId = "default_channel"
        val notificationManager = getSystemService(NotificationManager::class.java)

        // ایجاد Notification Channel برای اندروید 8 و بالاتر
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // ایجاد Intent برای هدایت به MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            if (!openUrl.isNullOrEmpty()) {
                putExtra("openUrl", openUrl) // اضافه کردن openUrl به Intent
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // ایجاد PendingIntent برای کلیک روی نوتیفیکیشن
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // ساخت نوتیفیکیشن
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // اگر URL تصویر وجود داشته باشد، تصویر را دانلود و به نوتیفیکیشن اضافه کن
        if (!imageUrl.isNullOrEmpty()) {
            try {
                val bitmap = downloadBitmap(imageUrl)
                if (bitmap != null) {
                    notificationBuilder
                        .setStyle(
                            NotificationCompat.BigPictureStyle()
                                .bigPicture(bitmap)
                                .bigLargeIcon(null as Bitmap?)
                        )
                        .setLargeIcon(bitmap)
                }
            } catch (e: Exception) {
                Log.e("FCM", "Failed to download image: $e")
            }
        }

        // نمایش نوتیفیکیشن
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    // متد برای دانلود تصویر از URL
    private fun downloadBitmap(imageUrl: String): Bitmap? {
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection()
            connection.doInput = true
            connection.connect()
            val input = connection.getInputStream()
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            Log.e("FCM", "Error downloading bitmap: $e")
            null
        }
    }
}
