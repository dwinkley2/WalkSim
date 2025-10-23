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
import com.dwinkley.walksim.utils.BroadcastHelper
import com.dwinkley.walksim.utils.StateManager
import com.dwinkley.walksim.ui.activities.MainActivity
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.abs

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
    private var currentRoad: Road? = null
    private var cumulativeDistances: List<Double> = emptyList()
    private var totalDistance: Double = 0.0

    fun startWalk(road: Road, speed: Float) {
        currentRoad = road
        val route = road.mRouteHigh
        cumulativeDistances = buildList {
            add(0.0)
            var cum = 0.0
            for (i in 0 until route.size - 1) {
                val d = route[i].distanceToAsDouble(route[i + 1])
                cum += d
                add(cum)
            }
        }
        totalDistance = cumulativeDistances.last()

        val startWalkAction = {
            stateManager.saveWalkState(road.mRouteHigh, speed)

            val intent = Intent(context, MockLocationService::class.java).apply {
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

        mapViewController.updateMockLocationMarker(
            latitude,
            longitude,
            bearing,
            isMoving,
            isWalking
        )

        val history = intent.getParcelableArrayListExtra<GeoPoint>("pathHistory")
        if (history != null) {
            mapViewController.drawWalkedPath(history)
        } else if (isMoving) {
            mapViewController.updateWalkedPath(currentPoint)
        }

        val remainingDistance = calculateRemainingDistance(currentPoint)
        val speed = intent.getFloatExtra("speed", 0f)
        BroadcastHelper.sendRemainingTimeUpdate(context, remainingDistance, speed)

        mapView.invalidate()
    }

    private fun calculateRemainingDistance(currentPoint: GeoPoint): Double {
        val route = currentRoad?.mRouteHigh ?: return 0.0

        for (i in 0 until route.size - 1) {
            val start = route[i]
            val end = route[i + 1]
            val dLat = end.latitude - start.latitude
            val dLon = end.longitude - start.longitude

            val fLat = if (dLat != 0.0) (currentPoint.latitude - start.latitude) / dLat else -1.0
            val fLon = if (dLon != 0.0) (currentPoint.longitude - start.longitude) / dLon else -1.0

            val fraction: Double = when {
                fLat < 0 && fLon < 0 -> continue
                fLat < 0 -> fLon
                fLon < 0 -> fLat
                abs(fLat - fLon) > 1e-6 -> continue
                else -> fLat
            }

            if (fraction in 0.0..1.0) {
                val segLen = cumulativeDistances[i + 1] - cumulativeDistances[i]
                val walkedInSeg = fraction * segLen
                val walkedTotal = cumulativeDistances[i] + walkedInSeg
                return totalDistance - walkedTotal
            }
        }

        var remainingDistance = 0.0
        var closestPointIndex = -1
        var minDistance = Double.MAX_VALUE

        for (i in route.indices) {
            val distance = route[i].distanceToAsDouble(currentPoint)
            if (distance < minDistance) {
                minDistance = distance
                closestPointIndex = i
            }
        }

        if (closestPointIndex != -1) {
            for (i in closestPointIndex until route.size - 1) {
                remainingDistance += route[i].distanceToAsDouble(route[i + 1])
            }
        }
        return remainingDistance
    }

    fun restoreWalkingState(road: Road, walkedPath: List<GeoPoint>?, speed: Float) {
        isWalking = true
        currentRoad = road

        val route = road.mRouteHigh
        cumulativeDistances = buildList {
            add(0.0)
            var cum = 0.0
            for (i in 0 until route.size - 1) {
                val d = route[i].distanceToAsDouble(route[i + 1])
                cum += d
                add(cum)
            }
        }
        totalDistance = cumulativeDistances.last()

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

        val tertiaryColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorTertiary,
            Color.RED
        )
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