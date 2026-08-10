package com.armatuhandroll.data.local

import android.content.Context
import java.util.UUID

/** Provides an app-installation identifier without using hardware or personal data. */
internal class AdminDeviceIdentity(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getOrCreateInstallationId(): String {
        preferences.getString(INSTALLATION_ID_KEY, null)?.let { storedId ->
            if (storedId.isNotBlank()) return storedId
        }

        return UUID.randomUUID().toString().also { generatedId ->
            preferences.edit().putString(INSTALLATION_ID_KEY, generatedId).apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "admin_device_identity"
        const val INSTALLATION_ID_KEY = "installation_id"
    }
}
