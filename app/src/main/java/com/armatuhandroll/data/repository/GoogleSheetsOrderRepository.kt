package com.armatuhandroll.data.repository

import android.util.Log
import com.armatuhandroll.domain.model.OrderRequest
import com.armatuhandroll.domain.repository.OrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val APPS_SCRIPT_ENDPOINT_URL = "https://script.google.com/macros/s/AKfycbzuA1_DjOwtrn0vl9pPEsfXExNFaLfW3akImx_Fd_nDMSxyTxYwRBOAk9sIMH4mbkPz7g/exec"

internal class GoogleSheetsOrderRepository : OrderRepository {
    override suspend fun sendOrder(order: OrderRequest): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("pedido_numero", order.orderNumber)
                put("fecha_hora", currentTimestamp())
                put("productos", order.products)
                put("cantidad_total", order.quantityTotal)
                put("total_pagado", order.totalPaid)
                put("tiempo_estimado", order.estimatedTime)
                put("nombre_usuario", order.username.trim())
                put("fcm_token", order.fcmToken.orEmpty())
            }.toString()

            val connection = (URL(APPS_SCRIPT_ENDPOINT_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 15_000
            }

            val result = runCatching {
                connection.outputStream.use { output ->
                    output.write(payload.toByteArray())
                }

                val responseCode = connection.responseCode
                val responseBody = runCatching {
                    val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }.getOrDefault("")

                Log.d(ORDER_LOG_TAG, "Respuesta webhook: code=$responseCode, body=$responseBody")

                check(responseCode in 200..299) {
                    "El webhook respondió con código HTTP $responseCode"
                }
                val response = JSONObject(responseBody)
                check(response.optBoolean("success")) {
                    response.optString("message", "No fue posible enviar el pedido")
                }
            }

            connection.disconnect()

            result
        }
    }

    override suspend fun reportTransfer(orderNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        postAction(
            JSONObject()
                .put("action", "reportTransfer")
                .put("orderNumber", orderNumber)
        )
    }

    private fun postAction(payload: JSONObject): Result<Unit> = runCatching {
        val connection = URL(APPS_SCRIPT_ENDPOINT_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode in 200..299) { "Error HTTP ${connection.responseCode}" }
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            check(response.optBoolean("success")) {
                response.optString("message", "No fue posible registrar la transferencia")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun currentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private companion object {
        const val ORDER_LOG_TAG = "OrderSheets"
    }
}
