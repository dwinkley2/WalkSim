package com.dwinkley.walksim.utils

object TimeUtils {
    fun calculateEstimatedTime(speedKmh: Float, distanceMeters: Double): String {
        if (distanceMeters <= 0) return "0 sec"

        val speedMs = speedKmh * 1000 / 3600
        if (speedMs <= 0) return "∞"

        val timeSeconds = (distanceMeters / speedMs).toLong()

        return when {
            timeSeconds < 60 -> "$timeSeconds sec"
            timeSeconds < 3600 -> {
                val minutes = timeSeconds / 60
                val seconds = timeSeconds % 60
                if (seconds == 0L) {
                    "${minutes} min"
                } else {
                    "${minutes}m ${seconds}s"
                }
            }
            timeSeconds < 86400 -> {
                val hours = timeSeconds / 3600
                val minutes = (timeSeconds % 3600) / 60
                "${hours}h ${minutes}min"
            }

            timeSeconds < 2592000 -> {
                val days = timeSeconds / 86400
                val hours = (timeSeconds % 86400) / 3600
                "${days}d ${hours}h"
            }

            timeSeconds < 31536000 -> {
                val months = timeSeconds / 2592000
                val days = (timeSeconds % 2592000) / 86400
                "${months}mo ${days}d"
            }

            else -> {
                val years = timeSeconds / 31536000
                val months = (timeSeconds % 31536000) / 2592000
                "${years}y ${months}mo"
            }
        }
    }
}