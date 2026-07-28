package com.armatuhandroll

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.armatuhandroll.navigation.AppNavigation
import com.armatuhandroll.ui.theme.ArmaTuHandrollTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val GoogleSheetsWebhookUrl = "https://script.google.com/macros/s/AKfycbzuA1_DjOwtrn0vl9pPEsfXExNFaLfW3akImx_Fd_nDMSxyTxYwRBOAk9sIMH4mbkPz7g/exec"
private const val OrderLogTag = "OrderSheets"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArmaTuHandrollTheme {
                AppNavigation(sendOrder = ::sendOrderToGoogleSheets)
            }
        }
    }
}

private suspend fun sendOrderToGoogleSheets(
    orderNumber: String,
    products: String,
    quantityTotal: Int,
    totalPaid: Int,
    estimatedTime: String,
    username: String
): Result<Unit> {
    return withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("pedido_numero", orderNumber)
            put("fecha_hora", currentTimestamp())
            put("productos", products)
            put("cantidad_total", quantityTotal)
            put("total_pagado", totalPaid)
            put("tiempo_estimado", estimatedTime)
            put("nombre_usuario", username.trim())
        }.toString()

        Log.d(OrderLogTag, "Payload de pedido: $payload")

        val connection = (URL(GoogleSheetsWebhookUrl).openConnection() as HttpURLConnection).apply {
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

            Log.d(OrderLogTag, "Respuesta webhook: code=$responseCode, body=$responseBody")

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
