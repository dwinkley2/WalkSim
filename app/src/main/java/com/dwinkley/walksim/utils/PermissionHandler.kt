package com.dwinkley.walksim.utils

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PermissionHandler(
    private val activity: AppCompatActivity,
    private val onPermissionGranted: () -> Unit
) {

    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                checkMockLocationSetting()
                onPermissionGranted()
            } else {
                Toast.makeText(activity, "Location permission denied.", Toast.LENGTH_SHORT).show()
            }
            if (permissions[Manifest.permission.POST_NOTIFICATIONS] == false) {
                Toast.makeText(activity, "Notification permission was not granted.", Toast.LENGTH_SHORT).show()
            }
        }

    fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    fun checkPermissionsAndSettings(): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions()
            return false
        }

        if (!isMockLocationEnabled()) {
            Toast.makeText(
                activity,
                "Please set this app as the mock location app in Developer Options.",
                Toast.LENGTH_LONG
            ).show()
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            return false
        }

        return true
    }

    private fun isMockLocationEnabled(): Boolean {
        return try {
            val appOpsManager = activity.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            (appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                android.os.Process.myUid(),
                activity.packageName
            ) == AppOpsManager.MODE_ALLOWED)
        } catch (e: Exception) {
            false
        }
    }

    private fun checkMockLocationSetting() {
        if (!isMockLocationEnabled()) {
            Toast.makeText(
                activity,
                "Please set this app as the mock location app in Developer Options.",
                Toast.LENGTH_LONG
            ).show()
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
    }
}