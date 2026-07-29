package com.armatuhandroll.data.local

import android.content.Context
import com.armatuhandroll.domain.cart.CartStorage
import com.armatuhandroll.domain.model.CartItem
import com.armatuhandroll.domain.model.IngredientCustomization
import org.json.JSONArray
import org.json.JSONObject

internal class SharedPreferencesCartStorage(
    context: Context
) : CartStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun loadItems(): List<CartItem> {
        val savedItems = preferences.getString(CART_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()

        return runCatching {
            val jsonItems = JSONArray(savedItems)
            buildList {
                for (index in 0 until jsonItems.length()) {
                    runCatching { jsonItems.getJSONObject(index).toCartItem() }
                        .getOrNull()
                        ?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    override fun saveItems(items: List<CartItem>) {
        runCatching {
            val jsonItems = JSONArray()
            items.forEach { jsonItems.put(it.toJson()) }
            preferences.edit().putString(CART_KEY, jsonItems.toString()).apply()
        }
    }

    override fun clear() {
        runCatching { preferences.edit().remove(CART_KEY).apply() }
    }

    private fun CartItem.toJson(): JSONObject = JSONObject().apply {
        put(PRODUCT_ID_KEY, productId)
        put(NAME_KEY, name)
        put(UNIT_PRICE_KEY, unitPrice)
        put(QUANTITY_KEY, quantity)
        put(CUSTOMIZATION_KEY, customization?.toJson() ?: JSONObject.NULL)
        put(FIXED_INGREDIENTS_KEY, fixedIngredients.toJsonArray())
        put(DETAILS_KEY, details.toJsonArray())
    }

    private fun JSONObject.toCartItem(): CartItem? = runCatching {
        CartItem(
            productId = getInt(PRODUCT_ID_KEY),
            name = getString(NAME_KEY),
            unitPrice = getInt(UNIT_PRICE_KEY),
            quantity = getInt(QUANTITY_KEY),
            customization = if (isNull(CUSTOMIZATION_KEY)) {
                null
            } else {
                getJSONObject(CUSTOMIZATION_KEY).toIngredientCustomization()
                    ?: error("Invalid customization")
            },
            fixedIngredients = getJSONArray(FIXED_INGREDIENTS_KEY).toStringList(),
            details = getJSONArray(DETAILS_KEY).toStringList()
        )
    }.getOrNull()

    private fun IngredientCustomization.toJson(): JSONObject = JSONObject().apply {
        put(PROTEINS_KEY, proteins.toJsonArray())
        put(BASES_KEY, bases.toJsonArray())
        put(VEGETABLES_KEY, vegetables.toJsonArray())
        put(CHARGE_BASE_EXTRAS_KEY, chargeBaseExtras)
    }

    private fun JSONObject.toIngredientCustomization(): IngredientCustomization? = runCatching {
        IngredientCustomization(
            proteins = getJSONArray(PROTEINS_KEY).toStringList(),
            bases = getJSONArray(BASES_KEY).toStringList(),
            vegetables = getJSONArray(VEGETABLES_KEY).toStringList(),
            chargeBaseExtras = getBoolean(CHARGE_BASE_EXTRAS_KEY)
        )
    }.getOrNull()

    private fun List<String>.toJsonArray(): JSONArray = JSONArray().apply {
        this@toJsonArray.forEach { put(it) }
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) {
            add(getString(index))
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "arma_tu_handroll_preferences"
        const val CART_KEY = "cart_items"
        const val PRODUCT_ID_KEY = "productId"
        const val NAME_KEY = "name"
        const val UNIT_PRICE_KEY = "unitPrice"
        const val QUANTITY_KEY = "quantity"
        const val CUSTOMIZATION_KEY = "customization"
        const val FIXED_INGREDIENTS_KEY = "fixedIngredients"
        const val DETAILS_KEY = "details"
        const val PROTEINS_KEY = "proteins"
        const val BASES_KEY = "bases"
        const val VEGETABLES_KEY = "vegetables"
        const val CHARGE_BASE_EXTRAS_KEY = "chargeBaseExtras"
    }
}
