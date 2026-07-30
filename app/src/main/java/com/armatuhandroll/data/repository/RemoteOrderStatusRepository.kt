package com.armatuhandroll.data.repository

import android.util.Log
import com.armatuhandroll.domain.model.OrderStatus
import com.armatuhandroll.domain.repository.OrderStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class RemoteOrderStatusRepository(
    private val endpointUrl: String
) : OrderStatusRepository {

    override suspend fun getStatus(
        orderNumber: String
    ): Result<OrderStatus> {
        if (endpointUrl.isBlank()) {
            Log.e(TAG, "Error consultando estado remoto: endpoint no configurado")
            return Result.failure(
                IllegalStateException(
                    "El endpoint remoto de estados no está configurado"
                )
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                Log.d(TAG, "Inicio consulta remota de estado: $orderNumber")

                val encodedOrderNumber = URLEncoder.encode(
                    orderNumber,
                    StandardCharsets.UTF_8.toString()
                )
                val separator = if (endpointUrl.contains("?")) "&" else "?"
                val requestUrl =
                    "$endpointUrl${separator}orderNumber=$encodedOrderNumber"

                var connection: HttpURLConnection? = null

                try {
                    connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/json")
                        connectTimeout = CONNECTION_TIMEOUT_MILLIS
                        readTimeout = READ_TIMEOUT_MILLIS
                    }

                    val responseCode = connection.responseCode
                    Log.d(TAG, "Respuesta HTTP $responseCode: $orderNumber")

                    if (responseCode !in 200..299) {
                        connection.errorStream?.bufferedReader()?.use { reader ->
                            reader.readText()
                        }
                        throw IllegalStateException(
                            "Error consultando estado. HTTP $responseCode"
                        )
                    }

                    val responseBody = connection.inputStream.bufferedReader().use { reader ->
                        reader.readText()
                    }
                    val statusValue = JSONObject(responseBody)
                        .optString("status")
                        .trim()

                    if (statusValue.isEmpty()) {
                        throw IllegalStateException(
                            "La respuesta remota no contiene un estado válido"
                        )
                    }

                    val status = OrderStatus.values().firstOrNull {
                        it.storageValue == statusValue ||
                            it.displayName.equals(statusValue, ignoreCase = true)
                    } ?: throw IllegalStateException(
                        "La respuesta remota contiene un estado desconocido"
                    )

                    status.also {
                        Log.d(TAG, "Estado remoto resuelto: $orderNumber")
                    }
                } finally {
                    connection?.disconnect()
                }
            }.onFailure {
                Log.e(TAG, "Error consultando estado remoto: $orderNumber")
            }
        }
    }

    private companion object {
        const val TAG = "OrderStatusRemote"
        const val CONNECTION_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 15_000
    }
}
