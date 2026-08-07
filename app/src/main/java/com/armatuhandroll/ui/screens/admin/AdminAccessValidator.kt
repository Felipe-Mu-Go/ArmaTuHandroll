package com.armatuhandroll.ui.screens.admin

internal interface AdminAccessValidator {
    fun validate(pin: String): Boolean
}

/**
 * Implementación temporal para desarrollo; no representa seguridad de producción.
 * Será reemplazada por validación remota del PIN y del dispositivo autorizado.
 *
 * Para pruebas locales, acepta únicamente el PIN de demostración `0000`.
 */
internal class DevelopmentAdminAccessValidator : AdminAccessValidator {
    override fun validate(pin: String): Boolean = pin == "0000"
}
