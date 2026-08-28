package com.armatuhandroll.domain.model

internal data class WebpayTransaction(
    val token: String,
    val formUrl: String,
    val redirectUrl: String
)
