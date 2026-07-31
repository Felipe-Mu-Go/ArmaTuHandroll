package com.armatuhandroll.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.armatuhandroll.core.connectivity.ConnectivityObserver
import com.armatuhandroll.core.connectivity.ConnectivityStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal class AndroidConnectivityObserver(context: Context) : ConnectivityObserver {
    private val applicationContext = context.applicationContext
    private val connectivityManager =
        applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val status: Flow<ConnectivityStatus> = callbackFlow {
        fun emitCurrentStatus() {
            trySend(currentStatus())
        }

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emitCurrentStatus()

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = emitCurrentStatus()

            override fun onLost(network: Network) = emitCurrentStatus()
        }

        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        emitCurrentStatus()

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (_: IllegalArgumentException) {
                // The callback may already have been unregistered by the system.
            }
        }
    }.distinctUntilChanged()

    override fun isCurrentlyConnected(): Boolean =
        currentStatus() == ConnectivityStatus.AVAILABLE

    private fun currentStatus(): ConnectivityStatus {
        val activeNetwork = connectivityManager.activeNetwork
            ?: return ConnectivityStatus.UNAVAILABLE
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return ConnectivityStatus.UNAVAILABLE
        val hasValidatedInternet =
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        return if (hasValidatedInternet) {
            ConnectivityStatus.AVAILABLE
        } else {
            ConnectivityStatus.UNAVAILABLE
        }
    }
}
