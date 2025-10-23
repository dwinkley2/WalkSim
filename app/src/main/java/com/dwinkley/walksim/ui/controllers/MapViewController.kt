package com.dwinkley.walksim.ui.controllers

import android.content.Context
import android.content.res.Configuration as SysConfig
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import com.dwinkley.walksim.R
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable

class MapViewController(
    private val context: Context,
    private val mapView: MapView
) {
    private var mockLocationMarker: Marker? = null
    private var walkedPathOverlay: Polyline? = null
    lateinit var myLocationOverlay: MyLocationNewOverlay
        private set

    fun setup() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)

        applyNightModeIfNeeded()
        setupMyLocationOverlay()
    }

    private fun applyNightModeIfNeeded() {
        val nightModeFlags = context.resources.configuration.uiMode and SysConfig.UI_MODE_NIGHT_MASK
        if (nightModeFlags == SysConfig.UI_MODE_NIGHT_YES) {
            val invertMatrix = floatArrayOf(
                -1.0f, 0.0f, 0.0f, 0.0f, 255f,
                0.0f, -1.0f, 0.0f, 0.0f, 255f,
                0.0f, 0.0f, -1.0f, 0.0f, 255f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
            )
            val colorFilter = android.graphics.ColorMatrixColorFilter(invertMatrix)
            mapView.overlayManager.tilesOverlay.setColorFilter(colorFilter)
        }
    }

    private fun setupMyLocationOverlay() {
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)

        val primaryColor = MaterialColors.getColor(
            context,
            android.R.attr.colorPrimary,
            ContextCompat.getColor(context, R.color.purple)
        )

        val personBitmap =
            getBitmapFromVectorDrawable(R.drawable.ic_person_pin, 50, 50, primaryColor)

        myLocationOverlay.setPersonIcon(personBitmap)
        myLocationOverlay.setDirectionArrow(personBitmap, personBitmap)
        myLocationOverlay.setPersonAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        myLocationOverlay.setDirectionAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        mapView.overlays.add(myLocationOverlay)
    }

    fun drawWalkedPath(points: List<GeoPoint>) {
        walkedPathOverlay?.let { mapView.overlays.remove(it) }

        val primaryColor = MaterialColors.getColor(
            context,
            android.R.attr.colorPrimary,
            ContextCompat.getColor(context, R.color.purple)
        )

        walkedPathOverlay = Polyline().apply {
            color = primaryColor
            outlinePaint.strokeWidth = 25f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            setPoints(points)
        }
        mapView.overlays.add(walkedPathOverlay)
        mapView.invalidate()
    }

    fun updateWalkedPath(currentPoint: GeoPoint) {
        walkedPathOverlay?.addPoint(currentPoint)
        mapView.invalidate()
    }

    fun clearWalkedPath() {
        walkedPathOverlay?.let { mapView.overlays.remove(it) }
    }

    fun initializeWalkedPathOverlay() {
        walkedPathOverlay?.let { mapView.overlays.remove(it) }

        val primaryColor = MaterialColors.getColor(
            context,
            android.R.attr.colorPrimary,
            ContextCompat.getColor(context, R.color.purple)
        )

        walkedPathOverlay = Polyline().apply {
            color = primaryColor
            outlinePaint.strokeWidth = 25f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
        }
        mapView.overlays.add(walkedPathOverlay)
    }


    fun updateMockLocationMarker(
        latitude: Double,
        longitude: Double,
        bearing: Float,
        isMoving: Boolean,
        isWalking: Boolean
    ) {
        val geoPoint = GeoPoint(latitude, longitude)

        if (mockLocationMarker == null) {
            if (!isWalking) return

            val primaryColor = MaterialColors.getColor(
                context,
                android.R.attr.colorPrimary,
                ContextCompat.getColor(context, R.color.purple)
            )

            val personBitmap =
                getBitmapFromVectorDrawable(R.drawable.ic_person_pin, 50, 50, primaryColor)

            mockLocationMarker = Marker(mapView).apply {
                icon = personBitmap?.toDrawable(context.resources)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(mockLocationMarker)
        }

        mockLocationMarker?.position = geoPoint
        mockLocationMarker?.rotation = bearing
        mockLocationMarker?.setVisible(isMoving)

        mapView.invalidate()
    }

    fun removeMockLocationMarker() {
        mockLocationMarker?.let { mapView.overlays.remove(it) }
        mockLocationMarker = null
        mapView.invalidate()
    }

    fun getBitmapFromVectorDrawable(drawableId: Int, width: Int, height: Int, color: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        drawable.draw(canvas)
        return bitmap
    }
}