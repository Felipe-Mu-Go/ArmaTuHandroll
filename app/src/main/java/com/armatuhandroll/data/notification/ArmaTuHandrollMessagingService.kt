package com.armatuhandroll.data.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal class ArmaTuHandrollMessagingService :
    FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d(
            TAG,
            "FCM token actualizado: $token",
        )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(
            TAG,
            "Mensaje FCM recibido desde: ${message.from}",
        )

        Log.d(
            TAG,
            "Datos recibidos: ${message.data.keys}",
        )

        Log.d(
            TAG,
            "Notificación recibida: ${message.notification != null}",
        )
    }

    private companion object {
        const val TAG = "ArmaTuHandrollFCM"
    }
}
