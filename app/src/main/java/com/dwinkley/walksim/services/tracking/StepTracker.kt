package com.dwinkley.walksim.services.tracking

import android.content.Context
import android.util.Log
import com.dwinkley.walksim.data.models.StepStats
import com.dwinkley.walksim.managers.HealthConnectManager
import com.dwinkley.walksim.utils.BroadcastHelper
import org.osmdroid.util.GeoPoint
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.roundToLong

class StepTracker(
    private val context: Context,
    private val healthConnectManager: HealthConnectManager,
    private val onStepUpdate: (Long) -> Unit
) {

    private var stats = StepStats(
        totalSteps = 0L,
        totalDistance = 0.0,
        walkStartTime = ZonedDateTime.now(),
        lastStepSyncTime = null
    )

    private var lastLocation: GeoPoint? = null

    companion object {
        private const val TAG = "StepTracker"
        private const val AVERAGE_STEP_LENGTH_METERS = 0.762
        private const val STEP_BATCH_INTERVAL_SECONDS = 60L
    }

    fun initialize(startLocation: GeoPoint) {
        stats = StepStats(
            totalSteps = 0L,
            totalDistance = 0.0,
            walkStartTime = ZonedDateTime.now(),
            lastStepSyncTime = null
        )
        lastLocation = startLocation
    }

    fun recordMovement(currentLocation: GeoPoint) {
        val lastLoc = lastLocation ?: return

        val distance = lastLoc.distanceToAsDouble(currentLocation)
        val newTotalDistance = stats.totalDistance + distance

        val steps = calculateSteps(distance)
        val newTotalSteps = stats.totalSteps + steps

        stats = stats.copy(
            totalSteps = newTotalSteps,
            totalDistance = newTotalDistance
        )

        lastLocation = currentLocation

        onStepUpdate(newTotalSteps)
        BroadcastHelper.sendStepUpdate(context, newTotalSteps)

        checkAndSyncSteps()
    }

    private fun calculateSteps(distanceMeters: Double): Long {
        return max(1L, (distanceMeters / AVERAGE_STEP_LENGTH_METERS).roundToLong())
    }

    private fun checkAndSyncSteps() {
        val now = ZonedDateTime.now()
        val lastSync = stats.lastStepSyncTime ?: stats.walkStartTime

        val secondsSinceSync = java.time.Duration.between(lastSync, now).seconds

        if (secondsSinceSync >= STEP_BATCH_INTERVAL_SECONDS && stats.totalSteps > 0) {
            syncStepsToHealthConnect(lastSync, now)
        }
    }

    private fun syncStepsToHealthConnect(startTime: ZonedDateTime, endTime: ZonedDateTime) {
        healthConnectManager.writeStepsAsync(startTime, endTime, stats.totalSteps) { success ->
            if (success) {
                stats = stats.copy(
                    totalSteps = 0L,
                    lastStepSyncTime = endTime
                )
                Log.d(TAG, "Steps synced successfully")
            }
        }
    }

    fun finalize(onComplete: () -> Unit) {
        val endTime = ZonedDateTime.now()
        val lastSync = stats.lastStepSyncTime ?: stats.walkStartTime

        if (stats.totalSteps > 0) {
            healthConnectManager.writeStepsAsync(lastSync, endTime, stats.totalSteps) { success ->
                if (success) {
                    Log.d(TAG, "Final step batch synced")
                }
            }
        }

        healthConnectManager.writeExerciseSessionAsync(stats.walkStartTime, endTime) { success ->
            if (success) {
                Log.d(TAG, "Exercise session saved")
            }
            onComplete()
        }
    }

    fun getCurrentStats(): StepStats = stats
}