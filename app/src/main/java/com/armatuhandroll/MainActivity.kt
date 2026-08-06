package com.armatuhandroll

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.armatuhandroll.data.local.SharedPreferencesCartStorage
import com.armatuhandroll.data.local.SharedPreferencesOrderHistoryStorage
import com.armatuhandroll.data.connectivity.AndroidConnectivityObserver
import com.armatuhandroll.data.notification.AndroidOrderStatusNotifier
import com.armatuhandroll.domain.cart.CartManager
import com.armatuhandroll.domain.history.OrderHistoryManager
import com.armatuhandroll.navigation.AppNavigation
import com.armatuhandroll.ui.theme.ArmaTuHandrollTheme
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    companion object {
        internal const val EXTRA_OPEN_ORDER_HISTORY =
            "com.armatuhandroll.extra.OPEN_ORDER_HISTORY"

        private const val FCM_LOG_TAG = "ArmaTuHandrollFCM"
    }

    private var openOrderHistoryRequested by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission denial does not affect the rest of the application. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        processNavigationIntent(intent)

        FirebaseMessaging.getInstance()
            .token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(
                        FCM_LOG_TAG,
                        "No fue posible obtener el token FCM.",
                        task.exception
                    )
                    return@addOnCompleteListener
                }

                val token = task.result

                Log.d(
                    FCM_LOG_TAG,
                    "Token FCM actual: $token"
                )
            }

        CartManager.initialize(
            SharedPreferencesCartStorage(applicationContext)
        )
        OrderHistoryManager.initialize(
            SharedPreferencesOrderHistoryStorage(applicationContext)
        )
        val connectivityObserver = AndroidConnectivityObserver(applicationContext)
        val orderStatusNotifier = AndroidOrderStatusNotifier(applicationContext)
        orderStatusNotifier.createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            ArmaTuHandrollTheme {
                AppNavigation(
                    connectivityObserver = connectivityObserver,
                    orderStatusNotifier = orderStatusNotifier,
                    openOrderHistoryRequested = openOrderHistoryRequested,
                    onOrderHistoryRequestConsumed = {
                        openOrderHistoryRequested = false
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processNavigationIntent(intent)
    }

    private fun processNavigationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ORDER_HISTORY, false) == true) {
            intent.removeExtra(EXTRA_OPEN_ORDER_HISTORY)
            openOrderHistoryRequested = true
        }
    }
}
