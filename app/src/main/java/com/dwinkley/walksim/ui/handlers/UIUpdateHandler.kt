package com.dwinkley.walksim.ui.handlers

import android.content.Context
import android.widget.TextView
import com.dwinkley.walksim.managers.RouteManager
import com.dwinkley.walksim.utils.TimeUtils
import com.google.android.material.slider.Slider

class UIUpdateHandler(
    private val context: Context,
    private val routeManager: RouteManager
) {

    fun updateSpeedLabel(speedLabel: TextView, speed: Float) {
        val distance = routeManager.getRouteDistance()
        val estimatedTime = TimeUtils.calculateEstimatedTime(speed, distance)
        speedLabel.text = "${speed.toInt()} km/h • $estimatedTime"
    }

    fun setupSpeedSlider(
        speedSlider: Slider,
        speedLabel: TextView,
        onSpeedChanged: (Float) -> Unit
    ) {
        speedSlider.addOnChangeListener { _, value, _ ->
            updateSpeedLabel(speedLabel, value)
            onSpeedChanged(value)
        }
    }
}