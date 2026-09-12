package com.legtracking.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert
    suspend fun insertTrack(track: TrackEntity): Long

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Delete
    suspend fun deleteTrack(track: TrackEntity)

    @Query("SELECT * FROM tracks ORDER BY startedAt DESC")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: Long): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY startedAt DESC")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Insert
    suspend fun insertPoint(point: TrackPointEntity)

    @Insert
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp ASC")
    suspend fun getPointsForTrack(trackId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points ORDER BY timestamp ASC")
    suspend fun getAllPointsOnce(): List<TrackPointEntity>

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    suspend fun deletePointsForTrack(trackId: Long)

    @Query("DELETE FROM tracks")
    suspend fun clearAllTracks()

    @Query("DELETE FROM track_points")
    suspend fun clearAllPoints()
}
