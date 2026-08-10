package com.armatuhandroll.data.repository

import com.armatuhandroll.domain.model.AdminOrder
import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.repository.AdminOrdersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class AppsScriptAdminOrdersRepository(
    private val endpointUrl: String
) : AdminOrdersRepository {
    override suspend fun getOrders(): Result<List<AdminOrder>> = withContext(Dispatchers.IO) {
        runCatching {
            val separator = if (endpointUrl.contains("?")) "&" else "?"
            val connection = URL("${endpointUrl}${separator}action=listOrders")
                .openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS

                check(connection.responseCode in 200..299) {
                    "Error consultando pedidos. HTTP ${connection.responseCode}"
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val payload = JSONObject(response)
                check(payload.optBoolean("success")) { "El listado de pedidos no fue exitoso" }
                val orders = payload.optJSONArray("orders")
                    ?: throw IllegalStateException("La respuesta no contiene pedidos")

                buildList {
                    for (index in 0 until orders.length()) {
                        val order = orders.getJSONObject(index)
                        add(order.toAdminOrder())
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun JSONObject.toAdminOrder() = AdminOrder(
        orderNumber = getString("orderNumber"),
        dateTime = getString("dateTime"),
        products = getString("products"),
        totalQuantity = optInt("totalQuantity"),
        totalPaid = optInt("totalPaid"),
        estimatedTime = getString("estimatedTime"),
        customerName = getString("customerName"),
        status = mapStatus(optString("status"))
    )

    private fun mapStatus(remoteStatus: String): OrderStatus {
        val normalizedStatus = if (remoteStatus.trim() == "ready") {
            "ready_for_pickup"
        } else {
            remoteStatus.trim()
        }
        return OrderStatus.values().firstOrNull { it.storageValue == normalizedStatus }
            ?: throw IllegalArgumentException("Estado de pedido desconocido: $remoteStatus")
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000
    }
}
