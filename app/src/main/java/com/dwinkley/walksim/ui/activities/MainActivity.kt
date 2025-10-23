package com.dwinkley.walksim.ui.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import com.dwinkley.walksim.R
import com.dwinkley.walksim.managers.HealthConnectManager
import com.dwinkley.walksim.managers.LocationManager
import com.dwinkley.walksim.managers.RouteManager
import com.dwinkley.walksim.managers.WalkSimulationManager
import com.dwinkley.walksim.ui.controllers.MapViewController
import com.dwinkley.walksim.ui.handlers.UIUpdateHandler
import com.dwinkley.walksim.utils.BroadcastHelper
import com.dwinkley.walksim.utils.PermissionHandler
import com.dwinkley.walksim.utils.StateManager
import com.dwinkley.walksim.utils.TimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView
import java.time.ZonedDateTime

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapViewController: MapViewController
    private lateinit var locationManager: LocationManager
    private lateinit var routeManager: RouteManager
    private lateinit var walkSimulationManager: WalkSimulationManager
    private lateinit var permissionHandler: PermissionHandler
    private lateinit var stateManager: StateManager
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var uiUpdateHandler: UIUpdateHandler

    private lateinit var speedSlider: Slider
    private lateinit var speedLabel: TextView
    private lateinit var walkToggleButton: MaterialButton
    private lateinit var stepCountDisplay: TextView
    private lateinit var remainingTimeDisplay: TextView

    private lateinit var healthConnectClient: HealthConnectClient

    private val healthConnectPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    )

    private val stepUpdateHandler = Handler(Looper.getMainLooper())
    private var pendingStepUpdateRunnable: Runnable? = null
    private var lastKnownStepCount = 0L

    private val autoStopHandler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null
    private var isRemainingZero = false

    private val stepsUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val stepCount = intent?.getLongExtra(BroadcastHelper.EXTRA_STEP_COUNT, -1L) ?: -1L
            if (stepCount >= 0) {
                scheduleStepDisplayUpdate(stepCount)
            }
        }
    }

    private val remainingTimeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val remainingDistance =
                intent?.getDoubleExtra(BroadcastHelper.EXTRA_REMAINING_DISTANCE, 0.0) ?: 0.0
            val speed = intent?.getFloatExtra(BroadcastHelper.EXTRA_SPEED, 0f) ?: 0f
            updateRemainingTimeDisplay(remainingDistance, speed)
        }
    }

    private val locationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BroadcastHelper.ACTION_LOCATION_UPDATE) {
                walkSimulationManager.handleLocationUpdate(intent)
            }
        }
    }

    private val requestHealthConnectPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        lifecycleScope.launch {
            if (permissions.all { it.value }) {
                stepCountDisplay.visibility = View.VISIBLE
                loadInitialStepCount()
                walkSimulationManager.onHealthConnectPermissionsGranted()
            } else {
                stepCountDisplay.visibility = View.GONE
                Toast.makeText(
                    this@MainActivity,
                    "Health Connect permissions not granted. Some features may be unavailable.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        org.osmdroid.config.Configuration.getInstance()
            .load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.activity_main)

        healthConnectClient = HealthConnectClient.getOrCreate(this)
        healthConnectManager = HealthConnectManager(this)

        initializeComponents()
        setupUI()
        restoreWalkState()
        setupHealthConnect()
    }

    private fun initializeComponents() {
        stateManager = StateManager(this)
        mapView = findViewById(R.id.map)
        mapViewController = MapViewController(this, mapView)

        routeManager = RouteManager(this, mapView)
        uiUpdateHandler = UIUpdateHandler(this, routeManager)

        locationManager = LocationManager(this, mapView, mapViewController)
        locationManager.routeManager = routeManager

        walkSimulationManager = WalkSimulationManager(
            this,
            mapView,
            mapViewController,
            stateManager,
            healthConnectManager,
            ::requestHealthConnectPermissionsIfNeeded
        )

        permissionHandler = PermissionHandler(this) {
            locationManager.startRealLocationUpdates()
        }

        mapViewController.setup()
        permissionHandler.requestPermissions()
    }

    private fun setupUI() {
        speedSlider = findViewById(R.id.speed_slider)
        speedLabel = findViewById(R.id.speed_label)
        walkToggleButton = findViewById(R.id.walk_toggle_button)
        stepCountDisplay = findViewById(R.id.step_count_display)
        remainingTimeDisplay = findViewById(R.id.remaining_time_display)

        val generateRouteButton = findViewById<Button>(R.id.generate_route_button)

        setupWalkToggleButton()
        setupGenerateRouteButton(generateRouteButton)
        uiUpdateHandler.setupSpeedSlider(speedSlider, speedLabel) { }
    }

    private fun setupWalkToggleButton() {
        walkToggleButton.setOnClickListener {
            if (walkSimulationManager.isWalking) {
                walkSimulationManager.stopWalk()
                locationManager.startRealLocationUpdates()
                remainingTimeDisplay.visibility = View.GONE
                cancelAutoStop()
            } else {
                if (permissionHandler.checkPermissionsAndSettings() && routeManager.hasRoute()) {
                    locationManager.stopRealLocationUpdates()
                    walkSimulationManager.startWalk(
                        routeManager.getCurrentRoad()!!,
                        speedSlider.value
                    )
                    remainingTimeDisplay.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupGenerateRouteButton(generateRouteButton: Button) {
        generateRouteButton.setOnClickListener {
            if (routeManager.hasStartAndEndPoints()) {
                routeManager.generateRoute { success ->
                    if (success) {
                        uiUpdateHandler.updateSpeedLabel(speedLabel, speedSlider.value)
                        showWalkControls()
                    } else {
                        Toast.makeText(this, "Error generating route", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showWalkControls() {
        speedSlider.visibility = View.VISIBLE
        speedLabel.visibility = View.VISIBLE
        walkToggleButton.visibility = View.VISIBLE
    }

    private fun restoreWalkState() {
        val savedState = stateManager.restoreWalkState() ?: return
        val (road, walkedPath, speed) = savedState

        routeManager.restoreRoute(road)
        walkSimulationManager.restoreWalkingState(road, walkedPath, speed)

        speedSlider.value = speed
        uiUpdateHandler.updateSpeedLabel(speedLabel, speed)
        showWalkControls()
        remainingTimeDisplay.visibility = View.VISIBLE

        locationManager.disableMyLocationOverlay()
        mapView.invalidate()
    }

    private fun scheduleStepDisplayUpdate(stepCount: Long) {
        pendingStepUpdateRunnable?.let { stepUpdateHandler.removeCallbacks(it) }

        if (stepCount == lastKnownStepCount) {
            return
        }

        pendingStepUpdateRunnable = Runnable {
            performStepDisplayUpdate(stepCount)
        }
        stepUpdateHandler.postDelayed(pendingStepUpdateRunnable!!, 100)
    }

    private fun performStepDisplayUpdate(stepCount: Long) {
        if (stepCount == lastKnownStepCount) return

        lastKnownStepCount = stepCount
        val formattedSteps = if (stepCount > 0) "%,d".format(stepCount) else "0"

        Log.d("MainActivity", "Updating step display to: $formattedSteps")
        stepCountDisplay.text = formattedSteps
    }

    private fun updateRemainingTimeDisplay(remainingDistance: Double, speed: Float) {
        val remainingTime = TimeUtils.calculateEstimatedTime(speed, remainingDistance)
        remainingTimeDisplay.text = "$remainingTime left"

        val isZero =
            remainingDistance <= 0.0
        if (isZero && !isRemainingZero) {
            isRemainingZero = true
            scheduleAutoStop()
        } else if (!isZero && isRemainingZero) {
            isRemainingZero = false
            cancelAutoStop()
        }
    }

    private fun scheduleAutoStop() {
        cancelAutoStop()
        autoStopRunnable = Runnable {
            if (walkSimulationManager.isWalking) {
                walkSimulationManager.stopWalk()
                locationManager.startRealLocationUpdates()
                remainingTimeDisplay.visibility = View.GONE
                Toast.makeText(this, "Walk completed", Toast.LENGTH_SHORT).show()
            }
        }
        autoStopHandler.postDelayed(autoStopRunnable!!, 3000)
    }

    private fun cancelAutoStop() {
        autoStopRunnable?.let { autoStopHandler.removeCallbacks(it) }
        autoStopRunnable = null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        registerReceivers()
        handleResumeActions()

        lifecycleScope.launch {
            if (hasAllHealthConnectPermissions()) {
                stepCountDisplay.visibility = View.VISIBLE
                loadInitialStepCount()
            } else {
                stepCountDisplay.visibility = View.GONE
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun registerReceivers() {
        val locFilter = IntentFilter(BroadcastHelper.ACTION_LOCATION_UPDATE)
        val stepFilter = IntentFilter(BroadcastHelper.ACTION_STEPS_UPDATED)
        val remainingTimeFilter = IntentFilter(BroadcastHelper.ACTION_REMAINING_TIME_UPDATE)

        registerReceiver(locationUpdateReceiver, locFilter, RECEIVER_EXPORTED)
        registerReceiver(stepsUpdatedReceiver, stepFilter, RECEIVER_NOT_EXPORTED)
        registerReceiver(remainingTimeReceiver, remainingTimeFilter, RECEIVER_NOT_EXPORTED)
    }

    private fun handleResumeActions() {
        if (walkSimulationManager.isWalking) {
            BroadcastHelper.sendPathHistoryRequest(this)
        } else {
            locationManager.startRealLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        unregisterReceiver(locationUpdateReceiver)
        unregisterReceiver(stepsUpdatedReceiver)
        unregisterReceiver(remainingTimeReceiver)
        locationManager.stopRealLocationUpdates()

        pendingStepUpdateRunnable?.let { stepUpdateHandler.removeCallbacks(it) }
        cancelAutoStop()
    }

    private fun requestHealthConnectPermissionsIfNeeded() {
        lifecycleScope.launch {
            if (!hasAllHealthConnectPermissions()) {
                requestHealthConnectPermissions.launch(healthConnectPermissions.toTypedArray())
            }
        }
    }

    private suspend fun hasAllHealthConnectPermissions(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.containsAll(healthConnectPermissions)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking Health Connect permissions", e)
            false
        }
    }

    private fun setupHealthConnect() {
        lifecycleScope.launch {
            if (hasAllHealthConnectPermissions()) {
                stepCountDisplay.visibility = View.VISIBLE
                loadInitialStepCount()
            } else {
                stepCountDisplay.visibility = View.GONE
                requestHealthConnectPermissions.launch(healthConnectPermissions.toTypedArray())
            }
        }
    }

    private fun loadInitialStepCount() {
        lifecycleScope.launch {
            val end = ZonedDateTime.now()
            val start = end.toLocalDate().atStartOfDay(end.zone)
            try {
                val response = healthConnectClient.aggregate(
                    androidx.health.connect.client.request.AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter.between(
                            start.toInstant(),
                            end.toInstant()
                        )
                    )
                )
                val stepCount = response[StepsRecord.COUNT_TOTAL] ?: 0L
                Log.d("HealthConnect", "Initial step count loaded: $stepCount")
                scheduleStepDisplayUpdate(stepCount)
            } catch (e: Exception) {
                Log.e("HealthConnect", "Error loading initial step count", e)
            }
        }
    }

    fun getWalkToggleButton(): MaterialButton = walkToggleButton
}