package com.armatuhandroll.data.notification

import com.armatuhandroll.core.notification.FcmTokenProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal class FirebaseFcmTokenProvider : FcmTokenProvider {

    override suspend fun getToken(): Result<String> =
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance()
                .token
                .addOnCompleteListener { task ->
                    if (!continuation.isActive) {
                        return@addOnCompleteListener
                    }

                    if (!task.isSuccessful) {
                        continuation.resume(
                            Result.failure(
                                task.exception
                                    ?: IllegalStateException(
                                        "No fue posible obtener el token FCM."
                                    )
                            )
                        )
                        return@addOnCompleteListener
                    }

                    val token = task.result?.trim().orEmpty()

                    if (token.isEmpty()) {
                        continuation.resume(
                            Result.failure(
                                IllegalStateException(
                                    "Firebase entregó un token FCM vacío."
                                )
                            )
                        )
                    } else {
                        continuation.resume(Result.success(token))
                    }
                }
        }
}
