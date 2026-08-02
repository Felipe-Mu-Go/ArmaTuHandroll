package com.armatuhandroll.core.notification

import com.armatuhandroll.domain.model.OrderStatus

internal interface OrderStatusNotifier {

    fun createNotificationChannel()

    fun notifyStatusChange(
        orderNumber: String,
        newStatus: OrderStatus
    )
}
