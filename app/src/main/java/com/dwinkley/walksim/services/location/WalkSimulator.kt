package com.dwinkley.walksim.services.location

import com.dwinkley.walksim.data.models.LocationUpdate
import com.dwinkley.walksim.data.models.WalkProgress
import com.dwinkley.walksim.data.models.WalkSimulationConfig
import org.osmdroid.util.GeoPoint
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class WalkSimulator(
    private val config: WalkSimulationConfig,
    private val onLocationUpdate: (LocationUpdate) -> Unit,
    private val onSimulationComplete: () -> Unit
) {

    private var executor: ScheduledExecutorService? = null
    private var progress = WalkProgress(
        currentPointIndex = 0,
        distanceAlongSegment = 0.0,
        lastSegmentBearing = calculateInitialBearing()
    )

    private fun calculateInitialBearing(): Float {
        return if (config.route.size > 1) {
            config.route[0].bearingTo(config.route[1]).toFloat()
        } else {
            0f
        }
    }

    fun start() {
        stop()
        executor = Executors.newSingleThreadScheduledExecutor()

        val distancePerInterval = config.speedMps * (config.updateIntervalMs / 1000.0)
        val task = createSimulationTask(distancePerInterval)

        executor?.execute(task)
    }

    private fun createSimulationTask(distancePerInterval: Double): Runnable {
        return object : Runnable {
            override fun run() {
                if (progress.currentPointIndex >= config.route.size - 1) {
                    onSimulationComplete()
                    return
                }

                progress = updateProgress(progress, distancePerInterval)
                executor?.schedule(this, config.updateIntervalMs, TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun updateProgress(
        current: WalkProgress,
        distancePerInterval: Double
    ): WalkProgress {
        var newDistance = current.distanceAlongSegment + distancePerInterval
        var index = current.currentPointIndex
        var bearing = current.lastSegmentBearing

        var startPoint = config.route[index]
        var endPoint = config.route[index + 1]
        var segmentLength = startPoint.distanceToAsDouble(endPoint)

        while (newDistance >= segmentLength) {
            val location = LocationUpdate(
                geoPoint = endPoint,
                bearing = bearing,
                isMoving = true,
                speed = (config.speedMps * 3.6).toFloat()
            )
            onLocationUpdate(location)

            newDistance -= segmentLength
            index++

            if (index >= config.route.size - 1) {
                return WalkProgress(index, newDistance, bearing)
            }

            startPoint = config.route[index]
            endPoint = config.route[index + 1]
            segmentLength = startPoint.distanceToAsDouble(endPoint)
            bearing = startPoint.bearingTo(endPoint).toFloat()
        }

        val fraction = if (segmentLength > 0) newDistance / segmentLength else 0.0
        val interpolatedPoint = GeoPoint(
            startPoint.latitude + (endPoint.latitude - startPoint.latitude) * fraction,
            startPoint.longitude + (endPoint.longitude - startPoint.longitude) * fraction
        )

        val location = LocationUpdate(
            geoPoint = interpolatedPoint,
            bearing = bearing,
            isMoving = true,
            speed = (config.speedMps * 3.6).toFloat()
        )
        onLocationUpdate(location)

        return WalkProgress(index, newDistance, bearing)
    }

    fun getRemainingDistance(): Double {
        if (progress.currentPointIndex >= config.route.size - 1) return 0.0

        val currentSegmentRemaining =
            config.route[progress.currentPointIndex].distanceToAsDouble(config.route[progress.currentPointIndex + 1]) - progress.distanceAlongSegment

        var remaining = currentSegmentRemaining
        for (i in progress.currentPointIndex + 1 until config.route.size - 1) {
            remaining += config.route[i].distanceToAsDouble(config.route[i + 1])
        }
        return remaining
    }

    fun stop() {
        executor?.shutdownNow()
        executor = null
    }
}