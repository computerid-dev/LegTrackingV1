package com.legtracking.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.legtracking.R
import com.legtracking.data.AppDatabase
import com.legtracking.databinding.ActivityMapBinding
import com.legtracking.service.LocationTrackingService
import com.legtracking.util.LocationSearchClient
import com.legtracking.util.NetworkMonitor
import com.legtracking.util.RouteClient
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TRACK_ID = "extra_track_id"
        private const val DEFAULT_ZOOM = 16.0
        private val DEFAULT_CENTER = GeoPoint(-6.200000, 106.816666) // fallback: Jakarta

        fun startWithTrack(context: Context, trackId: Long) {
            val intent = Intent(context, MapActivity::class.java)
            intent.putExtra(EXTRA_TRACK_ID, trackId)
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityMapBinding
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    private val searchClient = LocationSearchClient()
    private val routeClient = RouteClient()

    private var isTracking = false
    private var isOnline = true
    private var searchMarker: Marker? = null
    private var routeLine: Polyline? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            myLocationOverlay.enableMyLocation()
        } else {
            Toast.makeText(this, R.string.msg_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wajib dipanggil sebelum setContentView, atau osmdroid gagal load tile.
        Configuration.getInstance().load(
            this, android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        )

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMap()
        setupSearch()
        setupControls()

        networkMonitor = NetworkMonitor(this)
        networkMonitor.start { online ->
            runOnUiThread { updateOnlineStatus(online) }
        }

        ensureLocationPermission()

        val trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1L)
        if (trackId > 0) {
            loadSavedTrack(trackId)
        }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(DEFAULT_ZOOM)
        binding.mapView.controller.setCenter(DEFAULT_CENTER)

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView)
        myLocationOverlay.enableFollowLocation()
        binding.mapView.overlays.add(myLocationOverlay)
    }

    private fun setupSearch() {
        binding.inputSearch.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(textView.text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun setupControls() {
        binding.btnTrack.setOnClickListener {
            if (isTracking) {
                stopTracking()
            } else {
                promptTrackName()
            }
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun ensureLocationPermission() {
        val hasFine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine) {
            myLocationOverlay.enableMyLocation()
            return
        }

        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun promptTrackName() {
        val input = EditText(this)
        input.hint = getString(R.string.dialog_save_track_hint)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_save_track_title)
            .setView(input)
            .setPositiveButton(R.string.btn_start_tracking) { dialog, _ ->
                val name = input.text.toString().ifBlank { "Jejak" }
                startTracking(name)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun startTracking(name: String) {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_TRACK_NAME, name)
        }
        ContextCompat.startForegroundService(this, intent)

        isTracking = true
        binding.btnTrack.setText(R.string.btn_stop_tracking)
    }

    private fun stopTracking() {
        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        startService(intent)

        isTracking = false
        binding.btnTrack.setText(R.string.btn_start_tracking)
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return

        if (!isOnline) {
            Toast.makeText(this, R.string.msg_no_internet_search, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val results = searchClient.search(query, packageName)
            val first = results.firstOrNull() ?: return@launch

            val point = GeoPoint(first.latitude, first.longitude)
            showSearchMarker(point, first.displayName)
            binding.mapView.controller.animateTo(point)
            binding.mapView.controller.setZoom(DEFAULT_ZOOM)

            drawRouteFromCurrentLocation(point)
        }
    }

    private fun showSearchMarker(point: GeoPoint, title: String) {
        searchMarker?.let { binding.mapView.overlays.remove(it) }

        val marker = Marker(binding.mapView)
        marker.position = point
        marker.title = title
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        binding.mapView.overlays.add(marker)
        searchMarker = marker
        binding.mapView.invalidate()
    }

    private fun drawRouteFromCurrentLocation(destination: GeoPoint) {
        val myLocation = myLocationOverlay.myLocation ?: return

        lifecycleScope.launch {
            val route = routeClient.getRoute(myLocation, destination) ?: return@launch
            drawRoute(route.points)
        }
    }

    private fun drawRoute(points: List<GeoPoint>) {
        routeLine?.let { binding.mapView.overlays.remove(it) }

        val polyline = Polyline()
        polyline.setPoints(points)
        binding.mapView.overlays.add(polyline)
        routeLine = polyline
        binding.mapView.invalidate()
    }

    private fun loadSavedTrack(trackId: Long) {
        val dao = AppDatabase.getInstance(this).trackDao()
        lifecycleScope.launch {
            val points = dao.getPointsForTrack(trackId)
            if (points.isEmpty()) return@launch

            val geoPoints = points.map { GeoPoint(it.latitude, it.longitude) }
            drawRoute(geoPoints)

            binding.mapView.controller.setCenter(geoPoints.first())
            binding.mapView.controller.setZoom(DEFAULT_ZOOM)
        }
    }

    private fun updateOnlineStatus(online: Boolean) {
        isOnline = online
        binding.offlineBanner.visibility = if (online) {
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroy() {
        networkMonitor.stop()
        myLocationOverlay.disableMyLocation()
        super.onDestroy()
    }
}
