package com.dwinkley.walksim.data.models

import org.osmdroid.util.GeoPoint
import java.time.ZonedDateTime

data class WalkProgress(
    val currentPointIndex: Int,
    val distanceAlongSegment: Double,
    val lastSegmentBearing: Float
)

data class LocationUpdate(
    val geoPoint: GeoPoint,
    val bearing: Float,
    val isMoving: Boolean,
    val speed: Float
)

data class StepStats(
    val totalSteps: Long,
    val totalDistance: Double,
    val walkStartTime: ZonedDateTime,
    val lastStepSyncTime: ZonedDateTime?
)

data class WalkSimulationConfig(
    val route: ArrayList<GeoPoint>,
    val speedMps: Double,
    val updateIntervalMs: Long
)