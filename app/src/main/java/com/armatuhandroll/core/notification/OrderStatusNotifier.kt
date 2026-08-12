package com.armatuhandroll.core.notification

import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.model.RejectionReason

internal interface OrderStatusNotifier {

    fun createNotificationChannel()

    fun notifyStatusChange(
        orderNumber: String,
        newStatus: OrderStatus,
        rejectionReason: RejectionReason? = null
    )
}
