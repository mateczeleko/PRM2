package com.example.prm2

import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        //Added Notification
        val geofencingEvent = intent?.let { GeofencingEvent.fromIntent(it) }
        if (geofencingEvent != null) {
            if (geofencingEvent.hasError()) {
                val errorMessage = GeofenceStatusCodes
                    .getStatusCodeString(geofencingEvent.errorCode)
                Log.e(TAG, errorMessage)
                return
            }
        }
        // Get the transition type.
        if (geofencingEvent != null) {
            when (geofencingEvent.geofenceTransition) {

                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    sendNotification(context!!, "Enterance")
                }

                Geofence.GEOFENCE_TRANSITION_DWELL -> {
                    sendNotification(context!!, "Dwell")
                }

                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    sendNotification(context!!, "Exit")
                }

                else -> {
                    Log.e(TAG, "Error in setting up the geofence")
                }
            }
        }
    }

    private fun sendNotification(context: Context, s: String) {
        Log.d(TAG, "sendNotification: $s")
    }

}
