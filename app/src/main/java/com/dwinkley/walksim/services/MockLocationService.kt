package com.dwinkley.walksim.services

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.dwinkley.walksim.data.models.LocationUpdate
import com.dwinkley.walksim.data.models.WalkSimulationConfig
import com.dwinkley.walksim.managers.HealthConnectManager
import com.dwinkley.walksim.managers.ServiceNotificationManager
import com.dwinkley.walksim.services.location.MockLocationProvider
import com.dwinkley.walksim.services.location.WalkSimulator
import com.dwinkley.walksim.services.tracking.StepTracker
import com.dwinkley.walksim.utils.BroadcastHelper
import com.dwinkley.walksim.utils.StateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.time.ZonedDateTime

class MockLocationService : Service() {

    private val TAG = "MockLocationService"

    private lateinit var mockLocationProvider: MockLocationProvider
    private lateinit var notificationManager: ServiceNotificationManager
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var healthConnectClient: HealthConnectClient
    private lateinit var stateManager: StateManager
    private lateinit var stepTracker: StepTracker

    private var walkSimulator: WalkSimulator? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var pathHistory: ArrayList<GeoPoint> = ArrayList()
    private var lastBearing = 0f
    private var speedKmh: Float = 0f
    private var lastRemainingDistance: Double = 0.0
    private var lastTotalSteps: Long = 0L

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        initializeComponents()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initializeComponents() {
        mockLocationProvider = MockLocationProvider(this)
        notificationManager = ServiceNotificationManager(this)
        stateManager = StateManager(this)
        healthConnectManager = HealthConnectManager(this)
        healthConnectClient = HealthConnectClient.getOrCreate(this)

        stepTracker = StepTracker(this, healthConnectManager) { totalSteps ->
            readTodayStepsAndUpdateNotification()
        }

        mockLocationProvider.setupTestProvider()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        speedKmh = intent?.getFloatExtra("speed", 5.0f) ?: 5.0f
        val route = StateManager.currentRoute

        if (route.isNullOrEmpty()) {
            Log.e(TAG, "Route is null or empty, stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        initializeWalkSimulation(route, speedKmh)
        return START_STICKY
    }

    private fun initializeWalkSimulation(route: ArrayList<GeoPoint>, speedKmh: Float) {
        val config = WalkSimulationConfig(
            route = route,
            speedMps = speedKmh * 1000 / 3600.0,
            updateIntervalMs = 1000L
        )

        pathHistory.clear()
        stateManager.saveWalkState(route, speedKmh)
        lastRemainingDistance = calculateFullRouteDistance(route)
        stepTracker.initialize(route.first())

        walkSimulator = WalkSimulator(
            config = config,
            onLocationUpdate = ::handleLocationUpdate,
            onSimulationComplete = { stopSelf() }
        )

        startForeground(
            ServiceNotificationManager.NOTIFICATION_ID,
            notificationManager.createNotification(0L, lastRemainingDistance, speedKmh)
        )

        walkSimulator?.start()
    }

    private fun calculateFullRouteDistance(route: ArrayList<GeoPoint>): Double {
        var dist = 0.0
        for (i in 0 until route.size - 1) {
            dist += route[i].distanceToAsDouble(route[i + 1])
        }
        return dist
    }

    private fun handleLocationUpdate(update: LocationUpdate) {
        pathHistory.add(update.geoPoint)
        stateManager.saveWalkedPath(pathHistory)
        lastBearing = update.bearing

        mockLocationProvider.setMockLocation(
            update.geoPoint,
            update.bearing,
            update.speed
        )

        stepTracker.recordMovement(update.geoPoint)

        BroadcastHelper.sendLocationUpdate(
            context = this,
            latitude = update.geoPoint.latitude,
            longitude = update.geoPoint.longitude,
            bearing = update.bearing,
            isMoving = update.isMoving,
            speed = update.speed
        )

        lastRemainingDistance = walkSimulator!!.getRemainingDistance()
        notificationManager.updateNotification(lastTotalSteps, lastRemainingDistance, speedKmh)
    }

    private fun readTodayStepsAndUpdateNotification() {
        serviceScope.launch {
            try {
                val end = ZonedDateTime.now()
                val start = end.toLocalDate().atStartOfDay(end.zone)
                val response = healthConnectClient.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(
                            start.toInstant(),
                            end.toInstant()
                        )
                    )
                )
                val healthSteps = response[StepsRecord.COUNT_TOTAL] ?: 0L
                val currentSteps = stepTracker.getCurrentStats().totalSteps
                val totalDisplaySteps = healthSteps + currentSteps

                lastTotalSteps = totalDisplaySteps
                notificationManager.updateNotification(
                    totalDisplaySteps,
                    lastRemainingDistance,
                    speedKmh
                )
                BroadcastHelper.sendStepUpdate(this@MockLocationService, totalDisplaySteps)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading Health Connect steps", e)
                val currentSteps = stepTracker.getCurrentStats().totalSteps
                lastTotalSteps = currentSteps
                notificationManager.updateNotification(
                    currentSteps,
                    lastRemainingDistance,
                    speedKmh
                )
                BroadcastHelper.sendStepUpdate(this@MockLocationService, currentSteps)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun cleanup() {
        walkSimulator?.stop()

        stepTracker.finalize {
            Log.d(TAG, "Step tracking finalized")
        }

        stateManager.clearWalkState()
        mockLocationProvider.removeTestProvider()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}