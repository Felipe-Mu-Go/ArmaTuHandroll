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
            }.toString()

            Log.d(ORDER_LOG_TAG, "Payload de pedido: $payload")

            val connection = (URL(GOOGLE_SHEETS_WEBHOOK_URL).openConnection() as HttpURLConnection).apply {
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
            }

            connection.disconnect()

            result
        }
    }

    private fun currentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private companion object {
        const val GOOGLE_SHEETS_WEBHOOK_URL = "https://script.google.com/macros/s/AKfycbzuA1_DjOwtrn0vl9pPEsfXExNFaLfW3akImx_Fd_nDMSxyTxYwRBOAk9sIMH4mbkPz7g/exec"
        const val ORDER_LOG_TAG = "OrderSheets"
    }
}
