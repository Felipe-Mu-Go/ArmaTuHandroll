package com.armatuhandroll.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.armatuhandroll.MainActivity
import com.armatuhandroll.R
import com.armatuhandroll.core.notification.OrderStatusNotifier
import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.model.RejectionReason

private const val CHANNEL_ID = "order_status_updates"
private const val CHANNEL_NAME = "Estados de pedidos"
private const val CHANNEL_DESCRIPTION =
    "Notificaciones sobre cambios en el estado de los pedidos"
private const val NOTIFICATION_TITLE = "Arma Tu Handroll"

internal class AndroidOrderStatusNotifier(
    context: Context
) : OrderStatusNotifier {

    private val applicationContext = context.applicationContext

    override fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        runCatching {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            notificationManager().createNotificationChannel(channel)
        }
    }

    override fun notifyStatusChange(
        orderNumber: String,
        newStatus: OrderStatus,
        rejectionReason: RejectionReason?
    ) {
        val message = messageForStatus(orderNumber, newStatus, rejectionReason) ?: return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val notificationManager = notificationManager()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !notificationManager.areNotificationsEnabled()
            ) {
                return
            }

            val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                orderNumber.hashCode(),
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(applicationContext, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(applicationContext)
            }

            val notification = builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(
                    if (newStatus == OrderStatus.REJECTED) {
                        "Tu pedido no pudo ser aceptado"
                    } else if (newStatus == OrderStatus.CANCELLED) {
                        "Pedido cancelado"
                    } else {
                        NOTIFICATION_TITLE
                    }
                )
                .setContentText(message)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .build()

            notificationManager.notify(orderNumber.hashCode(), notification)
        }
    }

    private fun notificationManager(): NotificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    private fun messageForStatus(
        orderNumber: String,
        status: OrderStatus,
        rejectionReason: RejectionReason?
    ): String? =
        when (status) {
            OrderStatus.PENDING_REVIEW -> null
            OrderStatus.ACCEPTED -> "Tu pedido $orderNumber fue aceptado."
            OrderStatus.PENDING_PAYMENT -> "Tu pedido $orderNumber está pendiente de pago."
            OrderStatus.PAYMENT_REPORTED -> "Informaste el pago del pedido $orderNumber."
            OrderStatus.PAYMENT_CONFIRMED ->
                "El pago de tu pedido $orderNumber fue confirmado."
            OrderStatus.PREPARING -> "Tu pedido $orderNumber está en preparación."
            OrderStatus.READY_FOR_PICKUP ->
                "Tu pedido $orderNumber está listo para retirar."
            OrderStatus.DELIVERED ->
                "Tu pedido $orderNumber fue entregado. ¡Gracias por tu compra!"
            OrderStatus.REJECTED -> rejectionReason?.displayName
                ?: "El comercio no pudo aceptar tu pedido $orderNumber."
            OrderStatus.CANCELLED -> "Tu pedido $orderNumber fue cancelado."
        }
}
