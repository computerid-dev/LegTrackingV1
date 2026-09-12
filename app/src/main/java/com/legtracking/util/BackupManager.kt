package com.legtracking.util

import android.content.Context
import android.net.Uri
import com.legtracking.data.AppDatabase
import com.legtracking.data.TrackEntity
import com.legtracking.data.TrackPointEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Ekspor & impor seluruh histori jejak ke satu file JSON, supaya user bisa
 * pindah perangkat atau bikin cadangan tanpa perlu akun/server sama sekali.
 */
class BackupManager(private val context: Context) {

    private val db by lazy { AppDatabase.getInstance(context) }

    suspend fun exportToUri(uri: Uri): Boolean {
        return runCatching {
            val tracks = db.trackDao().getAllTracksOnce()
            val points = db.trackDao().getAllPointsOnce()

            val tracksJson = JSONArray()
            tracks.forEach { track ->
                tracksJson.put(
                    JSONObject().apply {
                        put("id", track.id)
                        put("name", track.name)
                        put("startedAt", track.startedAt)
                        put("endedAt", track.endedAt ?: JSONObject.NULL)
                        put("distanceMeters", track.distanceMeters)
                    }
                )
            }

            val pointsJson = JSONArray()
            points.forEach { point ->
                pointsJson.put(
                    JSONObject().apply {
                        put("trackId", point.trackId)
                        put("latitude", point.latitude)
                        put("longitude", point.longitude)
                        put("timestamp", point.timestamp)
                        put("accuracy", point.accuracy)
                    }
                )
            }

            val root = JSONObject().apply {
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                put("tracks", tracksJson)
                put("points", pointsJson)
            }

            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return false

            true
        }.getOrDefault(false)
    }

    suspend fun importFromUri(uri: Uri): Boolean {
        return runCatching {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            } ?: return false

            val root = JSONObject(content)
            val tracksJson = root.getJSONArray("tracks")
            val pointsJson = root.getJSONArray("points")

            // Petakan id lama -> id baru, karena autoGenerate id lokal bisa bentrok.
            val idMap = HashMap<Long, Long>()

            for (i in 0 until tracksJson.length()) {
                val obj = tracksJson.getJSONObject(i)
                val oldId = obj.getLong("id")
                val newTrack = TrackEntity(
                    name = obj.getString("name"),
                    startedAt = obj.getLong("startedAt"),
                    endedAt = if (obj.isNull("endedAt")) null else obj.getLong("endedAt"),
                    distanceMeters = obj.optDouble("distanceMeters", 0.0)
                )
                val newId = db.trackDao().insertTrack(newTrack)
                idMap[oldId] = newId
            }

            val newPoints = mutableListOf<TrackPointEntity>()
            for (i in 0 until pointsJson.length()) {
                val obj = pointsJson.getJSONObject(i)
                val oldTrackId = obj.getLong("trackId")
                val mappedId = idMap[oldTrackId] ?: continue
                newPoints.add(
                    TrackPointEntity(
                        trackId = mappedId,
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        timestamp = obj.getLong("timestamp"),
                        accuracy = obj.optDouble("accuracy", 0.0).toFloat()
                    )
                )
            }
            if (newPoints.isNotEmpty()) {
                db.trackDao().insertPoints(newPoints)
            }

            true
        }.getOrDefault(false)
    }
}
