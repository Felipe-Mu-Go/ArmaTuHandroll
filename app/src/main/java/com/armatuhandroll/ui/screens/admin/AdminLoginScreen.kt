package com.armatuhandroll.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.components.PrimaryActionButton
import com.armatuhandroll.ui.components.SecondaryActionButton
import com.armatuhandroll.ui.theme.CreamText

private const val MAX_PIN_LENGTH = 8

@Composable
internal fun AdminLoginScreen(
    validator: AdminAccessValidator,
    onAccessGranted: () -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var showAccessError by remember { mutableStateOf(false) }

    AppBackground {
        Scaffold(containerColor = Color.Transparent) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IngredientGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(24.dp)
                ) {
                    Text(
                        text = "Acceso administrador",
                        style = MaterialTheme.typography.headlineMedium,
                        color = CreamText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Arma Tu Handroll",
                        style = MaterialTheme.typography.titleMedium,
                        color = CreamText,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )
                    Text(
                        text = "Ingrese PIN de administrador",
                        color = CreamText,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { value ->
                            pin = value.filter(Char::isDigit).take(MAX_PIN_LENGTH)
                            showAccessError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = showAccessError,
                        supportingText = {
                            if (showAccessError) {
                                Text("Acceso no autorizado")
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CreamText,
                            unfocusedTextColor = CreamText,
                            focusedBorderColor = CreamText,
                            unfocusedBorderColor = CreamText.copy(alpha = 0.7f),
                            focusedLabelColor = CreamText,
                            unfocusedLabelColor = CreamText.copy(alpha = 0.8f),
                            errorTextColor = MaterialTheme.colorScheme.error,
                            cursorColor = CreamText
                        )
                    )
                    PrimaryActionButton(
                        text = "Ingresar",
                        onClick = {
                            if (validator.validate(pin)) {
                                pin = ""
                                onAccessGranted()
                            } else {
                                pin = ""
                                showAccessError = true
                            }
                        },
                        enabled = pin.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                    )
                    SecondaryActionButton(
                        text = "Cancelar",
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
            }
        }
    }
}
