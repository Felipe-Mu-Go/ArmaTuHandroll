package com.armatuhandroll.ui.util

import androidx.annotation.DrawableRes
import com.armatuhandroll.R
import com.armatuhandroll.domain.model.Product

@DrawableRes
internal fun Product.customizationBackgroundRes(): Int = when (name) {
    "Handroll" -> R.drawable.handrroll
    "Gohan" -> R.drawable.gohan
    "SushiBurger" -> R.drawable.sushiburger
    "SushiPleto" -> R.drawable.sushipleto
    else -> R.drawable.fondo
}
