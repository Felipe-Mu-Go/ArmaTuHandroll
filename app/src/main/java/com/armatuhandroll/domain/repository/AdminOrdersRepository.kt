package com.armatuhandroll.domain.repository

import com.armatuhandroll.domain.model.AdminOrder

internal interface AdminOrdersRepository {
    suspend fun getOrders(): Result<List<AdminOrder>>
}
