package com.dwinkley.walksim.services.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.osmdroid.util.GeoPoint

class MockLocationProvider(private val context: Context) {

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    companion object {
        private const val TAG = "MockLocationProvider"
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun setupTestProvider(): Boolean {
        return try {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false,
                true, true, true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            Log.d(TAG, "Test provider enabled successfully")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Did you forget to set the mock location app?", e)
            false
        }
    }

    fun setMockLocation(
        geoPoint: GeoPoint,
        bearing: Float,
        speedMps: Float
    ): Boolean {
        return try {
            val location = createLocation(geoPoint, bearing, speedMps)
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting mock location", e)
            false
        }
    }

    private fun createLocation(
        geoPoint: GeoPoint,
        bearing: Float,
        speedMps: Float
    ): Location {
        return Location(LocationManager.GPS_PROVIDER).apply {
            latitude = geoPoint.latitude
            longitude = geoPoint.longitude
            altitude = 0.0
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = System.nanoTime()
            this.bearing = bearing
            this.speed = speedMps
            accuracy = 5.0f
        }
    }

    fun removeTestProvider() {
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            Log.d(TAG, "Test provider removed")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove test provider", e)
        }
    }
}