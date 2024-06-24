package com.example.prm2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlin.math.*

class LocationCheckService : Service() {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private lateinit var locationHelper: LocationHelper
    private val entryStates = mutableMapOf<String, Boolean>() // true if inside radius, false if outside

    override fun onCreate() {
        super.onCreate()
        locationHelper = LocationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        coroutineScope.launch {
            checkLocations()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun checkLocations() {
        val db = FirebaseFirestore.getInstance()
        locationHelper.getLocationUpdates().collect { currentLocation ->
            getEntries(db) { entries ->
                entries.forEach { (id, entry) ->
                    entry.location?.let { locationString ->
                        val entryLocation = parseLocation(locationString)
                        val distance = calculateDistance(
                            currentLocation.latitude, currentLocation.longitude,
                            entryLocation.latitude, entryLocation.longitude
                        )

                        val wasInside = entryStates[id] ?: false
                        val isInside = distance <= 1000 // 1000 meters = 1 km

                        if (isInside && !wasInside) {
                            // Entered the radius
                            showNotification(id, entry.title)
                            entryStates[id] = true
                        } else if (!isInside && wasInside) {
                            // Left the radius
                            entryStates[id] = false
                        }
                    }
                }
            }
        }
    }

    private fun parseLocation(locationString: String): LatLng {
        val (lat, lng) = locationString.split(",").map { it.trim().toDouble() }
        return LatLng(lat, lng)
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // metres
        val φ1 = lat1 * PI / 180
        val φ2 = lat2 * PI / 180
        val Δφ = (lat2 - lat1) * PI / 180
        val Δλ = (lon2 - lon1) * PI / 180

        val a = sin(Δφ / 2) * sin(Δφ / 2) +
                cos(φ1) * cos(φ2) *
                sin(Δλ / 2) * sin(Δλ / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    private fun showNotification(entryId: String, title: String) {
        val channelId = "LocationAlerts"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(androidx.core.R.drawable.notification_bg_low)
            .setContentTitle("Entered Diary Entry Area")
            .setContentText("You've entered the area of your diary entry: $title")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(entryId.hashCode(), notificationBuilder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}