package com.example.prm2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.maps.android.compose.GoogleMap

@Composable
fun GoogleMapScreen(modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier = modifier.fillMaxSize()) {
        GoogleMap()
    }
}