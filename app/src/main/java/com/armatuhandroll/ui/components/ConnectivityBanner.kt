package com.armatuhandroll.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ConnectivityBanner(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    if (isConnected) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFFC857),
        contentColor = Color(0xFF241A00)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = "Sin conexión a Internet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Algunas funciones no estarán disponibles.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
