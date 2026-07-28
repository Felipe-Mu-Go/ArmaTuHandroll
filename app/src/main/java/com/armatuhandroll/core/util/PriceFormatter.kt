package com.armatuhandroll.core.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal fun formatPrice(value: Int): String {
    val symbols = DecimalFormatSymbols(Locale("es", "CL")).apply {
        groupingSeparator = '.'
    }
    return "$" + DecimalFormat("#,###", symbols).format(value)
}
