package com.dwinkley.walksim.managers

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.concurrent.thread

class RouteManager(
    private val context: Context,
    private val mapView: MapView,
) {
    private var road: Road? = null
    private var startPoint: GeoPoint? = null
    private var endPoint: GeoPoint? = null
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var roadOverlay: Polyline? = null

    fun hasRoute(): Boolean = road != null
    fun hasStartAndEndPoints(): Boolean = startPoint != null && endPoint != null
    fun getCurrentRoad(): Road? = road

    fun addStartMarker(p: GeoPoint) {
        startPoint = p
        startMarker?.let { mapView.overlays.remove(it) }
        val primaryColor = MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.BLUE)
        startMarker = Marker(mapView).apply {
            position = p
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Start"
            isDraggable = true
            val startIcon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()
            startIcon?.colorFilter = PorterDuffColorFilter(primaryColor, PorterDuff.Mode.SRC_IN)
            icon = startIcon
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker) {}
                override fun onMarkerDragEnd(marker: Marker) {
                    startPoint = marker.position
                }
                override fun onMarkerDragStart(marker: Marker) {}
            })
        }
        mapView.overlays.add(startMarker)
        mapView.invalidate()
    }

    fun addEndMarker(p: GeoPoint) {
        endPoint = p
        endMarker?.let { mapView.overlays.remove(it) }
        val tertiaryColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiary, Color.RED)
        endMarker = Marker(mapView).apply {
            position = p
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "End"
            isDraggable = true
            val endIcon = ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()
            endIcon?.colorFilter = PorterDuffColorFilter(tertiaryColor, PorterDuff.Mode.SRC_IN)
            icon = endIcon
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker) {}
                override fun onMarkerDragEnd(marker: Marker) {
                    endPoint = marker.position
                }
                override fun onMarkerDragStart(marker: Marker) {}
            })
        }
        mapView.overlays.add(endMarker)
        mapView.invalidate()
    }

    fun generateRoute(onComplete: (Boolean) -> Unit) {
        thread {
            val roadManager: RoadManager = OSRMRoadManager(context, "com.dwinkley.walksim")
            (roadManager as OSRMRoadManager).setMean(OSRMRoadManager.MEAN_BY_FOOT)
            val waypoints = arrayListOf(startPoint!!, endPoint!!)
            val newRoad = roadManager.getRoad(waypoints)

            (context as? android.app.Activity)?.runOnUiThread {
                road = newRoad
                roadOverlay?.let { mapView.overlays.remove(it) }
                val tertiaryColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiary, Color.RED)

                if (road!!.mStatus == Road.STATUS_OK) {
                    roadOverlay = RoadManager.buildRoadOverlay(road, tertiaryColor, 15f)
                    roadOverlay?.outlinePaint?.strokeCap = Paint.Cap.ROUND
                    roadOverlay?.outlinePaint?.strokeJoin = Paint.Join.ROUND
                    mapView.overlays.add(0, roadOverlay)
                    mapView.invalidate()
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            }
        }
    }

    fun restoreRoute(restoredRoad: Road) {
        road = restoredRoad
        val routePoints = restoredRoad.mRouteHigh

        val restoredStartPoint = routePoints.firstOrNull()
        val restoredEndPoint = routePoints.lastOrNull()
        if (restoredStartPoint != null && restoredEndPoint != null) {
            addStartMarker(restoredStartPoint)
            addEndMarker(restoredEndPoint)
        }

        val tertiaryColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiary, Color.RED)
        roadOverlay = RoadManager.buildRoadOverlay(road, tertiaryColor, 15f)
        roadOverlay?.outlinePaint?.strokeCap = Paint.Cap.ROUND
        roadOverlay?.outlinePaint?.strokeJoin = Paint.Join.ROUND
        mapView.overlays.add(0, roadOverlay)
    }

    fun createDefaultMarkers(centerPoint: GeoPoint) {
        if (startMarker == null) {
            val startGeoPoint = GeoPoint(centerPoint.latitude + 0.005, centerPoint.longitude - 0.005)
            val endGeoPoint = GeoPoint(centerPoint.latitude - 0.005, centerPoint.longitude + 0.005)
            addStartMarker(startGeoPoint)
            addEndMarker(endGeoPoint)
        }
    }
}