package com.armatuhandroll.data.repository

import com.armatuhandroll.domain.model.AdminOrder
import com.armatuhandroll.domain.model.AdminPayment
import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.model.PaymentMethod
import com.armatuhandroll.domain.model.RejectionReason
import com.armatuhandroll.domain.repository.AdminOrdersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class AppsScriptAdminOrdersRepository(
    private val endpointUrl: String,
    private val installationId: String
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

    override suspend fun updateOrderStatus(
        orderNumber: String,
        newStatus: OrderStatus
    ): Result<OrderStatus> = withContext(Dispatchers.IO) {
        runCatching {
            require(newStatus in ADMINISTRATIVE_STATUSES) {
                "Estado administrativo no permitido"
            }
            val connection = URL(endpointUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                val request = JSONObject()
                    .put("action", "updateOrderStatus")
                    .put("orderNumber", orderNumber)
                    .put("newStatus", newStatus.storageValue)
                    .put("installationId", installationId)
                connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }

                check(connection.responseCode in 200..299) {
                    "Error actualizando pedido. HTTP ${connection.responseCode}"
                }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val payload = JSONObject(response)
                check(payload.optBoolean("success")) {
                    payload.optString("message", "No fue posible actualizar el pedido")
                }
                mapStatus(payload.getString("status"))
            } finally {
                connection.disconnect()
            }
        }
    }

    override suspend fun getPayments(): Result<List<AdminPayment>> = withContext(Dispatchers.IO) {
        getJson("listPayments").map { payload ->
            val payments = payload.getJSONArray("payments")
            buildList {
                for (index in 0 until payments.length()) add(payments.getJSONObject(index).toAdminPayment())
            }
        }
    }

    override suspend fun rejectOrder(
        orderNumber: String,
        reason: RejectionReason,
        detail: String
    ): Result<OrderStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmedDetail = detail.trim()
            require(reason != RejectionReason.OTHER || trimmedDetail.length in 3..120) {
                "El detalle debe tener entre 3 y 120 caracteres"
            }
            val connection = URL(endpointUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                val request = JSONObject().put("action", "rejectOrder")
                    .put("orderNumber", orderNumber).put("reason", reason.storageValue)
                    .put("detail", trimmedDetail).put("installationId", installationId)
                connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                check(connection.responseCode in 200..299) { "Error rechazando pedido. HTTP ${connection.responseCode}" }
                val payload = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                check(payload.optBoolean("success")) {
                    payload.optString("message", "No fue posible rechazar el pedido")
                }
                mapStatus(payload.getString("status"))
            } finally { connection.disconnect() }
        }
    }

    override suspend fun registerPayment(
        orderNumber: String,
        paymentMethod: PaymentMethod
    ): Result<AdminPayment> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(endpointUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                val request = JSONObject().put("action", "registerPayment")
                    .put("orderNumber", orderNumber).put("paymentMethod", paymentMethod.storageValue)
                    .put("installationId", installationId)
                connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                check(connection.responseCode in 200..299) { "Error registrando pago. HTTP ${connection.responseCode}" }
                val payload = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                check(payload.optBoolean("success")) { payload.optString("message", "No fue posible registrar el pago") }
                payload.getJSONObject("payment").toAdminPayment()
            } finally { connection.disconnect() }
        }
    }

    override suspend fun confirmTransfer(orderNumber: String): Result<AdminPayment> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(endpointUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                val request = JSONObject().put("action", "confirmTransfer")
                    .put("orderNumber", orderNumber).put("installationId", installationId)
                connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                check(connection.responseCode in 200..299) { "Error confirmando transferencia. HTTP ${connection.responseCode}" }
                val payload = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                check(payload.optBoolean("success")) { payload.optString("message", "No fue posible confirmar la transferencia") }
                payload.getJSONObject("payment").toAdminPayment()
            } finally { connection.disconnect() }
        }
    }

    private fun getJson(action: String): Result<JSONObject> = runCatching {
        val separator = if (endpointUrl.contains("?")) "&" else "?"
        val connection = URL("${endpointUrl}${separator}action=$action").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            check(connection.responseCode in 200..299)
            val payload = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            check(payload.optBoolean("success")) { payload.optString("message") }
            payload
        } finally { connection.disconnect() }
    }

    private fun JSONObject.toAdminPayment() = AdminPayment(
        paymentId = getString("paymentId"), orderNumber = getString("orderNumber"),
        dateTime = getString("dateTime"), paymentMethod = PaymentMethod.fromStorageValue(getString("paymentMethod")),
        amount = optInt("amount"), paymentStatus = getString("paymentStatus"), isToday = optBoolean("isToday")
    )

    private fun JSONObject.toAdminOrder() = AdminOrder(
        orderNumber = getString("orderNumber"),
        dateTime = getString("dateTime"),
        products = getString("products"),
        totalQuantity = optInt("totalQuantity"),
        totalPaid = optInt("totalPaid"),
        estimatedTime = getString("estimatedTime"),
        customerName = getString("customerName"),
        status = mapStatus(optString("status")),
        paymentStatus = optString("paymentStatus", "pending"),
        paymentMethod = optString("paymentMethod"),
        paidAmount = optInt("paidAmount"),
        rejectionReason = RejectionReason.fromStorageValue(optString("rejectionReason")),
        rejectionDetail = optString("rejectionDetail")
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
        val ADMINISTRATIVE_STATUSES = setOf(
            OrderStatus.ACCEPTED,
            OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.DELIVERED
        )
    }
}
