package com.dwinkley.walksim.ui.handlers

import android.content.Context
import android.widget.TextView
import com.google.android.material.slider.Slider

class UIUpdateHandler(private val context: Context) {
    fun updateSpeedLabel(speedLabel: TextView, speed: Float) {
        speedLabel.text = "${speed.toInt()} km/h"
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