package com.armatuhandroll.domain.repository

internal interface AdminDeviceAuthorizationRepository {
    suspend fun isAuthorized(installationId: String): Result<Boolean>
}
