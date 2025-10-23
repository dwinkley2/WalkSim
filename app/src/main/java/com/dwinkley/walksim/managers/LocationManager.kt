package com.dwinkley.walksim.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.dwinkley.walksim.ui.controllers.MapViewController
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class LocationManager(
    private val context: Context,
    private val mapView: MapView,
    private val mapViewController: MapViewController
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    var routeManager: RouteManager? = null

    fun startRealLocationUpdates(initialCenter: GeoPoint? = null) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            mapViewController.myLocationOverlay.isEnabled = true
            mapViewController.myLocationOverlay.enableMyLocation()
            mapViewController.myLocationOverlay.enableFollowLocation()

            if (initialCenter != null) {
                mapView.controller.animateTo(initialCenter)
                routeManager?.createDefaultMarkers(initialCenter)
            } else {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val geoPoint = GeoPoint(location.latitude, location.longitude)
                        mapView.controller.animateTo(geoPoint)
                        routeManager?.createDefaultMarkers(geoPoint)
                    }
                }
            }
        }
    }

    fun stopRealLocationUpdates() {
        mapViewController.myLocationOverlay.disableFollowLocation()
        mapViewController.myLocationOverlay.disableMyLocation()
        mapViewController.myLocationOverlay.isEnabled = false
    }

    fun disableMyLocationOverlay() {
        mapViewController.myLocationOverlay.isEnabled = false
    }
}