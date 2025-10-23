package com.dwinkley.walksim.managers

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dwinkley.walksim.services.MockLocationService
import com.dwinkley.walksim.ui.controllers.MapViewController
import com.dwinkley.walksim.utils.StateManager
import com.dwinkley.walksim.ui.activities.MainActivity
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class WalkSimulationManager(
    private val context: Context,
    private val mapView: MapView,
    private val mapViewController: MapViewController,
    private val stateManager: StateManager,
    private val healthConnectManager: HealthConnectManager,
    private val requestPermissions: () -> Unit
) {
    var isWalking = false
        private set

    private var pendingStartWalk: (() -> Unit)? = null

    fun startWalk(road: Road, speed: Float) {
        val startWalkAction = {
            val intent = Intent(context, MockLocationService::class.java).apply {
                putParcelableArrayListExtra("route", road.mRouteHigh)
                putExtra("speed", speed)
            }
            ContextCompat.startForegroundService(context, intent)
            isWalking = true
            updateWalkToggleButton()

            mapViewController.clearWalkedPath()
            mapViewController.initializeWalkedPathOverlay()
        }

        (context as AppCompatActivity).lifecycleScope.launch {
            if (healthConnectManager.hasAllPermissions()) {
                startWalkAction()
            } else {
                pendingStartWalk = startWalkAction
                requestPermissions()
            }
        }
    }

    fun onHealthConnectPermissionsGranted() {
        pendingStartWalk?.invoke()
        pendingStartWalk = null
    }

    fun stopWalk() {
        context.stopService(Intent(context, MockLocationService::class.java))
        isWalking = false
        updateWalkToggleButton()

        mapViewController.removeMockLocationMarker()
        Toast.makeText(context, "Walk session saved", Toast.LENGTH_SHORT).show()
    }

    fun handleLocationUpdate(intent: Intent) {
        if (!isWalking) {
            mapViewController.removeMockLocationMarker()
            return
        }

        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        val bearing = intent.getFloatExtra("bearing", 0f)
        val isMoving = intent.getBooleanExtra("isMoving", false)
        val currentPoint = GeoPoint(latitude, longitude)

        mapViewController.updateMockLocationMarker(latitude, longitude, bearing, isMoving, isWalking)

        val history = intent.getParcelableArrayListExtra<GeoPoint>("pathHistory")
        if (history != null) {
            mapViewController.drawWalkedPath(history)
        } else if (isMoving) {
            mapViewController.updateWalkedPath(currentPoint)
        }
        mapView.invalidate()
    }

    fun restoreWalkingState(road: Road, walkedPath: List<GeoPoint>?, speed: Float) {
        isWalking = true
        updateWalkToggleButton()

        if (walkedPath != null && walkedPath.isNotEmpty()) {
            mapViewController.drawWalkedPath(walkedPath)
            val lastPoint = walkedPath.last()
            mapView.controller.setCenter(lastPoint)
        } else {
            val startPoint = road.mRouteHigh.firstOrNull()
            if (startPoint != null) {
                mapView.controller.setCenter(startPoint)
            }
        }
    }

    private fun updateWalkToggleButton() {
        val activity = context as? MainActivity ?: return
        val walkToggleButton = activity.getWalkToggleButton()

        val tertiaryColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiary, Color.RED)
        val primaryColor = MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.BLUE)

        if (isWalking) {
            walkToggleButton.text = "Stop Walk"
            walkToggleButton.setBackgroundColor(tertiaryColor)
        } else {
            walkToggleButton.text = "Start Walk"
            walkToggleButton.setBackgroundColor(primaryColor)
        }
    }
}