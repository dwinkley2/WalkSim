package com.dwinkley.walksim.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    private val historyRequestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BroadcastHelper.ACTION_REQUEST_PATH_HISTORY) {
                sendPathHistoryUpdate()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        initializeComponents()
        setupBroadcastReceiver()
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setupBroadcastReceiver() {
        val filter = IntentFilter(BroadcastHelper.ACTION_REQUEST_PATH_HISTORY)
        registerReceiver(historyRequestReceiver, filter, RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val route = intent?.getParcelableArrayListExtra<GeoPoint>("route")
        val speedKmh = intent?.getFloatExtra("speed", 5.0f) ?: 5.0f

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
        stepTracker.initialize(route.first())

        walkSimulator = WalkSimulator(
            config = config,
            onLocationUpdate = ::handleLocationUpdate,
            onSimulationComplete = { stopSelf() }
        )

        startForeground(
            ServiceNotificationManager.NOTIFICATION_ID,
            notificationManager.createNotification(0)
        )

        walkSimulator?.start()
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
    }

    private fun sendPathHistoryUpdate() {
        val lastPoint = pathHistory.lastOrNull() ?: return
        BroadcastHelper.sendLocationUpdate(
            context = this,
            latitude = lastPoint.latitude,
            longitude = lastPoint.longitude,
            bearing = lastBearing,
            isMoving = true,
            speed = 0f,
            pathHistory = pathHistory
        )
    }

    private fun readTodayStepsAndUpdateNotification() {
        serviceScope.launch {
            try {
                val end = ZonedDateTime.now()
                val start = end.toLocalDate().atStartOfDay(end.zone)
                val response = healthConnectClient.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start.toInstant(), end.toInstant())
                    )
                )
                val healthSteps = response[StepsRecord.COUNT_TOTAL] ?: 0L
                val currentSteps = stepTracker.getCurrentStats().totalSteps
                val totalDisplaySteps = healthSteps + currentSteps

                notificationManager.updateNotification(totalDisplaySteps)
                BroadcastHelper.sendStepUpdate(this@MockLocationService, totalDisplaySteps)
            } catch (e: Exception) {
                Log.e(TAG, "Error reading Health Connect steps", e)
                val currentSteps = stepTracker.getCurrentStats().totalSteps
                notificationManager.updateNotification(currentSteps)
                BroadcastHelper.sendStepUpdate(this@MockLocationService, currentSteps)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun cleanup() {
        unregisterReceiver(historyRequestReceiver)
        walkSimulator?.stop()

        stepTracker.finalize {
            Log.d(TAG, "Step tracking finalized")
        }

        stateManager.clearWalkState()
        mockLocationProvider.removeTestProvider()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}