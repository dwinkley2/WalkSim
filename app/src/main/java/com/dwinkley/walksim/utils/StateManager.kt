package com.dwinkley.walksim.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.util.GeoPoint

class StateManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        const val PREFS_NAME = "WalkSimPrefs"
        const val KEY_IS_WALKING = "isWalking"
        const val KEY_ROUTE = "route"
        const val KEY_WALKED_PATH = "walkedPath"
        const val KEY_SPEED = "speed"
    }

    data class SavedWalkState(
        val road: Road,
        val walkedPath: List<GeoPoint>?,
        val speed: Float
    )

    fun saveWalkState(route: ArrayList<GeoPoint>, speed: Float) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_IS_WALKING, true)
        val routeJson = gson.toJson(route)
        editor.putString(KEY_ROUTE, routeJson)
        editor.putFloat(KEY_SPEED, speed)
        editor.remove(KEY_WALKED_PATH)
        editor.apply()
    }

    fun saveWalkedPath(pathHistory: ArrayList<GeoPoint>) {
        val pathJson = gson.toJson(pathHistory)
        sharedPreferences.edit().putString(KEY_WALKED_PATH, pathJson).apply()
    }

    fun clearWalkState() {
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_IS_WALKING, false)
        editor.remove(KEY_ROUTE)
        editor.remove(KEY_WALKED_PATH)
        editor.remove(KEY_SPEED)
        editor.apply()
    }

    fun restoreWalkState(): SavedWalkState? {
        val isWalking = sharedPreferences.getBoolean(KEY_IS_WALKING, false)
        if (!isWalking) return null

        val routeJson = sharedPreferences.getString(KEY_ROUTE, null) ?: return null
        val walkedPathJson = sharedPreferences.getString(KEY_WALKED_PATH, null)
        val speed = sharedPreferences.getFloat(KEY_SPEED, 5f)

        val routeType = object : TypeToken<ArrayList<GeoPoint>>() {}.type
        val routePoints: ArrayList<GeoPoint> = gson.fromJson(routeJson, routeType)
        val road = Road(routePoints)

        val walkedPath = if (walkedPathJson != null) {
            val walkedPathType = object : TypeToken<ArrayList<GeoPoint>>() {}.type
            gson.fromJson<ArrayList<GeoPoint>>(walkedPathJson, walkedPathType)
        } else {
            null
        }

        return SavedWalkState(road, walkedPath, speed)
    }

    fun isWalking(): Boolean = sharedPreferences.getBoolean(KEY_IS_WALKING, false)
}