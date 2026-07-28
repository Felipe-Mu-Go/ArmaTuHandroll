package com.armatuhandroll.navigation

internal object AppRoutes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val CART = "cart"
    const val CUSTOMIZED_SUMMARY = "customized_summary"
    const val ORDER_CONFIRMATION = "order_confirmation"
    const val CUSTOMIZE = "customize/{productId}"
    const val CUSTOMIZE_EDIT = "customize/{productId}/{editIndex}"

    fun customize(productId: Int): String = "customize/$productId"

    fun customizeEdit(productId: Int, editIndex: Int): String =
        "customize/$productId/$editIndex"
}
