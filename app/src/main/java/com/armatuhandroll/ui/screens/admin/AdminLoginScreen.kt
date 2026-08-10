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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.armatuhandroll.ui.AppBackground
import com.armatuhandroll.ui.components.IngredientGlassCard
import com.armatuhandroll.ui.components.PrimaryActionButton
import com.armatuhandroll.ui.components.SecondaryActionButton
import com.armatuhandroll.ui.theme.CreamText
import com.armatuhandroll.domain.repository.AdminDeviceAuthorizationRepository
import kotlinx.coroutines.launch

private const val MAX_PIN_LENGTH = 8

@Composable
internal fun AdminLoginScreen(
    validator: AdminAccessValidator,
    installationId: String,
    authorizationRepository: AdminDeviceAuthorizationRepository,
    onAccessGranted: () -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var showAccessError by remember { mutableStateOf(false) }
    var authorizationState by remember { mutableStateOf<AuthorizationState>(AuthorizationState.Idle) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val isValidating = authorizationState == AuthorizationState.Validating

    fun validateDevice() {
        if (isValidating) return
        authorizationState = AuthorizationState.Validating
        coroutineScope.launch {
            authorizationRepository.isAuthorized(installationId)
                .onSuccess { authorized ->
                    if (authorized) {
                        pin = ""
                        authorizationState = AuthorizationState.Idle
                        onAccessGranted()
                    } else {
                        authorizationState = AuthorizationState.Unauthorized
                    }
                }
                .onFailure {
                    authorizationState = AuthorizationState.ConnectionError
                }
        }
    }

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
                            authorizationState = AuthorizationState.Idle
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
                    when (authorizationState) {
                        AuthorizationState.Validating -> Text(
                            text = "Validando dispositivo...",
                            color = CreamText,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        AuthorizationState.Unauthorized -> {
                            Text(
                                text = "Este dispositivo no está autorizado.\n\nID del dispositivo:\n$installationId",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                            SecondaryActionButton(
                                text = "Copiar ID",
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(installationId))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            )
                        }
                        AuthorizationState.ConnectionError -> {
                            Text(
                                text = "No fue posible validar este dispositivo",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                            SecondaryActionButton(
                                text = "Reintentar",
                                onClick = { validateDevice() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            )
                        }
                        AuthorizationState.Idle -> Unit
                    }
                    PrimaryActionButton(
                        text = if (isValidating) "Validando dispositivo..." else "Ingresar",
                        onClick = {
                            if (validator.validate(pin)) {
                                validateDevice()
                            } else {
                                pin = ""
                                showAccessError = true
                                authorizationState = AuthorizationState.Idle
                            }
                        },
                        enabled = pin.isNotEmpty() && !isValidating,
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

private enum class AuthorizationState {
    Idle,
    Validating,
    Unauthorized,
    ConnectionError
}
