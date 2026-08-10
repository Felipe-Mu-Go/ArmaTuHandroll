package com.armatuhandroll.data.repository

import com.armatuhandroll.domain.repository.AdminDeviceAuthorizationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class AppsScriptAdminDeviceAuthorizationRepository(
    private val endpointUrl: String
) : AdminDeviceAuthorizationRepository {
    override suspend fun isAuthorized(installationId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val separator = if (endpointUrl.contains("?")) "&" else "?"
                val encodedId = URLEncoder.encode(
                    installationId,
                    StandardCharsets.UTF_8.name()
                )
                val connection = URL(
                    "${endpointUrl}${separator}action=validateAdminDevice&installationId=$encodedId"
                ).openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/json")
                    connection.connectTimeout = TIMEOUT_MILLIS
                    connection.readTimeout = TIMEOUT_MILLIS

                    check(connection.responseCode in 200..299) {
                        "Error validando dispositivo. HTTP ${connection.responseCode}"
                    }

                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val payload = JSONObject(response)
                    check(payload.optBoolean("success")) {
                        "La validación del dispositivo no fue exitosa"
                    }
                    check(payload.has("authorized")) {
                        "La respuesta no contiene autorización"
                    }
                    payload.getBoolean("authorized")
                } finally {
                    connection.disconnect()
                }
            }
        }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000
    }
}
