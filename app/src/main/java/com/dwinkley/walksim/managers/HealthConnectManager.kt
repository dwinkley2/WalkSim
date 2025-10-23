package com.dwinkley.walksim.managers

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        val ALL_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        )

        private const val TAG = "HealthConnectManager"
    }

    suspend fun hasAllPermissions(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.containsAll(ALL_PERMISSIONS)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }

    fun writeStepsAsync(
        start: ZonedDateTime,
        end: ZonedDateTime,
        steps: Long,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (steps <= 0) {
            onComplete?.invoke(false)
            return
        }

        scope.launch {
            try {
                val metadata = Metadata.activelyRecorded(device = Device(type = Device.TYPE_PHONE))
                val stepsRecord = StepsRecord(
                    count = steps,
                    startTime = start.toInstant(),
                    endTime = end.toInstant(),
                    startZoneOffset = start.offset,
                    endZoneOffset = end.offset,
                    metadata = metadata
                )
                healthConnectClient.insertRecords(listOf(stepsRecord))
                Log.d(TAG, "Inserted $steps steps between ${start.toLocalTime()}–${end.toLocalTime()}")
                onComplete?.invoke(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert step batch", e)
                onComplete?.invoke(false)
            }
        }
    }

    fun writeExerciseSessionAsync(
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
        title: String = "Walk",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val metadata = Metadata.activelyRecorded(device = Device(type = Device.TYPE_PHONE))
                val session = ExerciseSessionRecord(
                    metadata = metadata,
                    startTime = startTime.toInstant(),
                    startZoneOffset = startTime.offset,
                    endTime = endTime.toInstant(),
                    endZoneOffset = endTime.offset,
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                    title = title
                )

                healthConnectClient.insertRecords(listOf(session))
                Log.d(TAG, "Exercise session saved: $title")
                onComplete?.invoke(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing walk session", e)
                onComplete?.invoke(false)
            }
        }
    }
}