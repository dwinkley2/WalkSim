package com.dwinkley.walksim.utils

import android.content.Context
import android.content.Intent
import org.osmdroid.util.GeoPoint

object BroadcastHelper {

    const val ACTION_LOCATION_UPDATE = "com.dwinkley.walksim.ACTION_LOCATION_UPDATE"
    const val ACTION_REQUEST_PATH_HISTORY = "com.dwinkley.walksim.ACTION_REQUEST_PATH_HISTORY"
    const val ACTION_STEPS_UPDATED = "com.dwinkley.walksim.ACTION_STEPS_UPDATED"

    const val EXTRA_LATITUDE = "latitude"
    const val EXTRA_LONGITUDE = "longitude"
    const val EXTRA_BEARING = "bearing"
    const val EXTRA_IS_MOVING = "isMoving"
    const val EXTRA_SPEED = "speed"
    const val EXTRA_PATH_HISTORY = "pathHistory"
    const val EXTRA_STEP_COUNT = "stepCount"

    fun sendLocationUpdate(
        context: Context,
        latitude: Double,
        longitude: Double,
        bearing: Float,
        isMoving: Boolean,
        speed: Float,
        pathHistory: ArrayList<GeoPoint>? = null
    ) {
        val intent = Intent(ACTION_LOCATION_UPDATE).apply {
            putExtra(EXTRA_LATITUDE, latitude)
            putExtra(EXTRA_LONGITUDE, longitude)
            putExtra(EXTRA_BEARING, bearing)
            putExtra(EXTRA_IS_MOVING, isMoving)
            putExtra(EXTRA_SPEED, speed)
            pathHistory?.let { putParcelableArrayListExtra(EXTRA_PATH_HISTORY, it) }
        }
        context.sendBroadcast(intent)
    }

    fun sendStepUpdate(context: Context, stepCount: Long) {
        val intent = Intent(ACTION_STEPS_UPDATED).apply {
            putExtra(EXTRA_STEP_COUNT, stepCount)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    fun sendPathHistoryRequest(context: Context) {
        context.sendBroadcast(Intent(ACTION_REQUEST_PATH_HISTORY))
    }
}