package com.legtracking.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceMeters: Double = 0.0
)
