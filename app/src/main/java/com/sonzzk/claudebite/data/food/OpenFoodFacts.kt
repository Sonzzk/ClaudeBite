package com.sonzzk.claudebite.data.food

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free food database (openfoodfacts.org): barcode lookups and text search. No API key, no tokens.
 * Uses plain HttpURLConnection + org.json so no extra libraries are needed.
 */
object OpenFoodFacts {
    private const val FIELDS = "code,product_name,brands,nutriments,serving_size,serving_quantity"
    private const val UA = "ClaudeBite/2.0 (Android; open-source calorie tracker)"

    suspend fun byBarcode(code: String): FoodProduct? = withContext(Dispatchers.IO) {
        val json = get("https://world.openfoodfacts.org/api/v2/product/${code.filter { it.isDigit() }}.json?fields=$FIELDS")
            ?: return@withContext null
        if (json.optInt("status") != 1) return@withContext null
        json.optJSONObject("product")?.let { parse(it, code) }
    }

    suspend fun search(query: String, limit: Int = 15): List<FoodProduct> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = get(
            "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1" +
                "&page_size=$limit&fields=$FIELDS",
        ) ?: return@withContext emptyList()
        val arr = json.optJSONArray("products") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parse(it, it.optString("code")) } }
    }

    private fun get(url: String): JSONObject? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", UA)
        try {
            if (c.responseCode != 200) return null
            JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    internal fun parse(p: JSONObject, code: String?): FoodProduct? {
        val name = p.optString("product_name").trim()
        if (name.isEmpty()) return null
        val n = p.optJSONObject("nutriments") ?: return null
        fun num(key: String): Float = n.optDouble(key, Double.NaN).let { if (it.isNaN() || it < 0) 0f else it.toFloat() }
        var kcal = num("energy-kcal_100g")
        if (kcal == 0f) kcal = num("energy_100g") / 4.184f // kJ -> kcal
        if (kcal == 0f && num("proteins_100g") == 0f && num("carbohydrates_100g") == 0f) return null
        val servingQty = p.optDouble("serving_quantity", Double.NaN).takeIf { !it.isNaN() && it > 0 }?.toFloat()
        val servingSize = p.optString("serving_size").trim()
        return FoodProduct(
            name = name.take(60),
            brand = p.optString("brands").split(',').firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.take(30),
            kcal100 = kcal,
            protein100 = num("proteins_100g"),
            carbs100 = num("carbohydrates_100g"),
            fat100 = num("fat_100g"),
            fiber100 = num("fiber_100g"),
            sugar100 = num("sugars_100g"),
            servingG = servingQty ?: 100f,
            servingLabel = if (servingQty != null && servingSize.isNotEmpty()) servingSize.take(24) else "100 g",
            source = FoodProduct.Source.OPEN_FOOD_FACTS,
            barcode = code?.takeIf { it.isNotBlank() },
        )
    }
}
