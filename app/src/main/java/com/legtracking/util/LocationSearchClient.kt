package com.legtracking.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class SearchResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Pencarian tempat pakai Nominatim, layanan geocoding gratis dari
 * proyek OpenStreetMap. Tidak butuh API key.
 */
class LocationSearchClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, appPackageName: String): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val url = "https://nominatim.openstreetmap.org/search" +
                "?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&format=json&limit=5"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", appPackageName)
                .build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val array = JSONArray(body)
                    val results = mutableListOf<SearchResult>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        results.add(
                            SearchResult(
                                displayName = obj.optString("display_name"),
                                latitude = obj.getString("lat").toDouble(),
                                longitude = obj.getString("lon").toDouble()
                            )
                        )
                    }
                    results
                }
            }.getOrDefault(emptyList())
        }
}
