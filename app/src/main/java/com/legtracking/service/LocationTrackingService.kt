package com.legtracking.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.legtracking.R
import com.legtracking.data.AppDatabase
import com.legtracking.data.TrackEntity
import com.legtracking.data.TrackPointEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    companion object {
        const val ACTION_START = "com.legtracking.action.START"
        const val ACTION_STOP = "com.legtracking.action.STOP"
        const val EXTRA_TRACK_NAME = "extra_track_name"

        private const val NOTIF_CHANNEL_ID = "tracking_channel"
        private const val NOTIF_ID = 1001
        private const val UPDATE_INTERVAL_MS = 5000L
        private const val MIN_UPDATE_INTERVAL_MS = 2000L
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: AppDatabase

    private var currentTrackId: Long = -1L
    private var lastLocation: Location? = null
    private var totalDistanceMeters: Double = 0.0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            onNewLocation(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = AppDatabase.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            else -> {
                val trackName = intent?.getStringExtra(EXTRA_TRACK_NAME) ?: "Jejak"
                startTracking(trackName)
            }
        }
        return START_STICKY
    }

    private fun startTracking(trackName: String) {
        startForeground()

        serviceScope.launch {
            val track = TrackEntity(
                name = trackName,
                startedAt = System.currentTimeMillis(),
                endedAt = null
            )
            currentTrackId = db.trackDao().insertTrack(track)
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .build()

        runCatching {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        }
    }

    private fun onNewLocation(location: Location) {
        val trackId = currentTrackId
        if (trackId <= 0) return

        lastLocation?.let { previous ->
            totalDistanceMeters += previous.distanceTo(location)
        }
        lastLocation = location

        serviceScope.launch {
            db.trackDao().insertPoint(
                TrackPointEntity(
                    trackId = trackId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis(),
                    accuracy = location.accuracy
                )
            )
        }
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)

        val trackId = currentTrackId
        val distance = totalDistanceMeters
        if (trackId > 0) {
            serviceScope.launch {
                val existing = db.trackDao().getTrackById(trackId)
                if (existing != null) {
                    db.trackDao().updateTrack(
                        existing.copy(
                            endedAt = System.currentTimeMillis(),
                            distanceMeters = distance
                        )
                    )
                }
            }
        }

        currentTrackId = -1L
        lastLocation = null
        totalDistanceMeters = 0.0

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_tracking_title))
            .setContentText(getString(R.string.notif_tracking_body))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.notif_tracking_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
