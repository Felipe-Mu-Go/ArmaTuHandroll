package com.armatuhandroll

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.armatuhandroll.data.local.SharedPreferencesCartStorage
import com.armatuhandroll.domain.cart.CartManager
import com.armatuhandroll.navigation.AppNavigation
import com.armatuhandroll.ui.theme.ArmaTuHandrollTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CartManager.initialize(
            SharedPreferencesCartStorage(applicationContext)
        )
        setContent {
            ArmaTuHandrollTheme {
                AppNavigation()
            }
        }
    }
}
