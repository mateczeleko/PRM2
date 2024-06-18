package com.example.prm2

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.prm2.ui.theme.PRM2Theme
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.GoogleMap
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Locale


@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    private val db: FirebaseFirestore by lazy { Firebase.firestore }
    private val RADIUS = 100f
    private val geofenceList = mutableListOf<Geofence>()
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
    lateinit var geofencingClient: GeofencingClient
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        geofencingClient = LocationServices.getGeofencingClient(this)
//        createGeofence(googleMap = null)
//        removeGeofence()
        enableEdgeToEdge()
        setContent {
            PRM2Theme {
                val navController = rememberNavController()
                var diaryEntries by remember { mutableStateOf(listOf<DiaryEntry>()) }
                fun refreshEntries() {
                    getEntries(db) { entries ->
                        diaryEntries = entries
                    }
                }

                LaunchedEffect(Unit) {
                    refreshEntries()
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp),
                    topBar = {
                        TopAppBar(title = { Button(onClick = { navController.navigate(route = "home") }, content = { Text("Diary")} ) })
                    }
                ) { innerPadding ->
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(navController = navController, diaryEntries = diaryEntries, modifier = Modifier.padding(innerPadding))
                        }
                        composable("addEntry") {
                            DiaryEntryScreen(
                                onSave = { entry ->
                                    addEntry(entry, db)
                                    refreshEntries()
                                    navController.navigate("home")
                                },
                                getLocation = { callback ->
                                    getCurrentLocation(callback)
                                }
                            )
                        }
                    }
                }
            }

        }

    }
    fun getCityName(lat: Double,long: Double):String{
        val geoCoder = Geocoder(this, Locale.getDefault())
        return geoCoder.getFromLocation(lat,long,1)?.let { address ->
            return address[0].locality
        } ?: "unknown city"
    }
    fun getGeofenceRequest(): GeofencingRequest {
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofenceList)
        }.build()
    }
    fun addGeofenceRequest() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        geofencingClient.addGeofences(getGeofenceRequest(), geofencePendingIntent).run {
            addOnSuccessListener {
                Toast.makeText(
                    this@MainActivity,
                    "Geofence is added successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }
            addOnFailureListener {
                Log.e("Error", it.localizedMessage)
                Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun createGeofence(googleMap: GoogleMap) {
        //Write here for the geo fence
        googleMap.setOnMapClickListener { latLng ->
            geofenceList.add(
                Geofence.Builder()
                    .setRequestId("entry.key")
                    .setCircularRegion(latLng.latitude, latLng.longitude, RADIUS)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()
            )
//            drawCircleOnMap(latLng)
            addGeofenceRequest()
        }
    }
    fun removeGeofence() {
        geofencingClient.removeGeofences(geofencePendingIntent).run {
            addOnSuccessListener {
                Toast.makeText(
                    this@MainActivity,
                    "Geofence is removed successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }
            addOnFailureListener {
                Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(callback: (Location?) -> Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                callback(location)
            }
    }
    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }
    @Composable
    fun DiaryEntryScreen(onSave: (DiaryEntry) -> Unit, getLocation: (callback: (Location?) -> Unit) -> Unit) {
        var title by remember { mutableStateOf(TextFieldValue()) }
        var content by remember { mutableStateOf(TextFieldValue()) }
        var imageUri by remember { mutableStateOf<Uri?>(null) }
        var audioUri by remember { mutableStateOf<Uri?>(null) }
        var location by remember { mutableStateOf<Location?>(null) }
        val context = LocalContext.current
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "New Diary Entry",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            TextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Button(onClick = { /* launch image picker */ }) {
                Text("Add Image")
            }
            Button(onClick = { /* launch audio recorder */ }) {
                Text("Add Audio")
            }
            Button(
                onClick = {
                    getLocation { loc ->
                        location = loc
                        val entry = DiaryEntry(
                            title = title.text,
                            content = content.text,
                            imageUrl = imageUri?.toString(),
                            audioUrl = audioUri?.toString(),
                            location = location?.let { "${it.latitude}, ${it.longitude}" },
                            cityName = getCityName(location?.latitude ?: 0.0, location?.longitude ?: 0.0),
                        )
                        onSave(entry)
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save")
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, diaryEntries: List<DiaryEntry>, modifier: Modifier) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diary Entries") },
                actions = {
                    IconButton(onClick = { navController.navigate("addEntry") }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(id = R.string.add_entry))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("addEntry") },
                content = { Icon(Icons.Filled.Add, contentDescription = stringResource(id = R.string.add_entry)) }
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(diaryEntries) { entry ->
                DiaryEntryCard(entry)
            }
        }
    }
}
@Composable
fun DiaryEntryCard(entry: DiaryEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = entry.title,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            entry.cityName?.let {
                Text(
                    text = "Location: $it",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = "${formatDate(entry.timestamp)}",
            )
        }
    }
}
fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault())
    val date = java.util.Date(timestamp)
    return sdf.format(date)
}






