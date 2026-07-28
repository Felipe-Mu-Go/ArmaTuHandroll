package com.armatuhandroll.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun IngredientCategory(
    title: String,
    subtitle: String,
    options: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit
) {
    IngredientGlassCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.92f))
            options.forEach { option ->
                val isSelected = selected.contains(option)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(option) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(option, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (isSelected) "✓" else "○", color = if (isSelected) Color(0xFF8BF6A0) else Color.White)
                }
            }
        }
    }
}
