package com.armatuhandroll.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.armatuhandroll.MainActivity
import com.armatuhandroll.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal class ArmaTuHandrollMessagingService :
    FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d(TAG, "Token FCM actualizado")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "Mensaje FCM recibido")

        val title = message.notification?.title?.trim()
        val body = message.notification?.body?.trim()
        val orderNumber = message.data["orderNumber"]?.trim()
        @Suppress("UNUSED_VARIABLE")
        val status = message.data["status"]?.trim()

        val notificationTitle = title ?: DEFAULT_TITLE
        if (notificationTitle.isEmpty() || body.isNullOrEmpty()) return

        publishNotification(notificationTitle, body, orderNumber)
    }

    private fun publishNotification(
        title: String,
        body: String,
        orderNumber: String?,
    ) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !notificationManager.areNotificationsEnabled()
            ) {
                return
            }

            val notificationId = orderNumber
                ?.takeIf(String::isNotEmpty)
                ?.hashCode()
                ?: FALLBACK_NOTIFICATION_ID
            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, ORDER_STATUS_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
            val notification = builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .build()

            notificationManager.notify(notificationId, notification)
        }
    }

    private companion object {
        const val TAG = "ArmaTuHandrollFCM"
        const val ORDER_STATUS_CHANNEL_ID = "order_status_updates"
        const val DEFAULT_TITLE = "Arma Tu Handroll"
        const val FALLBACK_NOTIFICATION_ID = 27_001
    }
}
