package com.example.prm2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState

@Composable
fun GoogleMapScreen(modifier: Modifier = Modifier.fillMaxSize(), entries: Map<String, DiaryEntry>) {
    Column(modifier = modifier.fillMaxSize()) {
        GoogleMap() {
            entries.forEach {
                Marker(
                    state = rememberMarkerState(
                        position =
                        LatLng(it.value.location?.split(",")?.get(0)?.toDouble() ?: 0.0,
                            it.value.location?.split(",")?.get(1)?.toDouble() ?: 0.0),
                        )

                )

            }
        }
    }
}