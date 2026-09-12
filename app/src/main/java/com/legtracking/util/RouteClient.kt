package com.legtracking.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.util.concurrent.TimeUnit

data class RouteResult(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double
)

/**
 * Ambil rute jalan antara dua titik lewat OSRM demo server (project-osrm.org),
 * layanan routing open-source gratis, tanpa API key.
 */
class RouteClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getRoute(start: GeoPoint, end: GeoPoint): RouteResult? =
        withContext(Dispatchers.IO) {
            val coordinates = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
            val url = "https://router.project-osrm.org/route/v1/foot/$coordinates" +
                "?overview=full&geometries=geojson"

            val request = Request.Builder().url(url).build()

            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    val root = JSONObject(body)
                    if (root.optString("code") != "Ok") return@withContext null

                    val route = root.getJSONArray("routes").getJSONObject(0)
                    val geometry = route.getJSONObject("geometry")
                    val coords = geometry.getJSONArray("coordinates")

                    val points = mutableListOf<GeoPoint>()
                    for (i in 0 until coords.length()) {
                        val pair = coords.getJSONArray(i)
                        val lon = pair.getDouble(0)
                        val lat = pair.getDouble(1)
                        points.add(GeoPoint(lat, lon))
                    }

                    RouteResult(
                        points = points,
                        distanceMeters = route.optDouble("distance", 0.0),
                        durationSeconds = route.optDouble("duration", 0.0)
                    )
                }
            }.getOrNull()
        }
}
