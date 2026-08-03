package com.armatuhandroll.core.notification

internal interface FcmTokenProvider {

    suspend fun getToken(): Result<String>
}
