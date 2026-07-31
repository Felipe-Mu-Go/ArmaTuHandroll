package com.armatuhandroll.core.connectivity

import kotlinx.coroutines.flow.Flow

internal interface ConnectivityObserver {
    val status: Flow<ConnectivityStatus>

    fun isCurrentlyConnected(): Boolean
}
