package com.dwinkley.walksim.managers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.dwinkley.walksim.R
import com.dwinkley.walksim.utils.TimeUtils

class ServiceNotificationManager(private val context: Context) {

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        const val CHANNEL_ID = "mock_location_service_channel"
        const val CHANNEL_NAME = "Mock Location Service"
        const val NOTIFICATION_ID = 1
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    fun createNotification(
        totalSteps: Long,
        remainingDistance: Double,
        speed: Float
    ): Notification {
        val stepText = if (totalSteps > 0) {
            "Total Steps: ${formatStepCount(totalSteps)}"
        } else {
            ""
        }

        val timeText = if (remainingDistance > 0) {
            val time = TimeUtils.calculateEstimatedTime(speed, remainingDistance)
            " • Remaining: $time"
        } else {
            ""
        }

        val contentText = if (stepText.isNotEmpty() || timeText.isNotEmpty()) {
            stepText + timeText
        } else {
            "Following route..."
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Simulating Walk")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    fun updateNotification(totalSteps: Long, remainingDistance: Double, speed: Float) {
        notificationManager.notify(
            NOTIFICATION_ID,
            createNotification(totalSteps, remainingDistance, speed)
        )
    }

    private fun formatStepCount(steps: Long): String {
        return "%,d".format(steps)
    }
}