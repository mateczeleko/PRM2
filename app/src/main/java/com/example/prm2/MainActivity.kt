package com.example.prm2

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private val db: FirebaseFirestore by lazy { Firebase.firestore }
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PRM2Theme {
                val navController = rememberNavController()
                var diaryEntries by remember { mutableStateOf(listOf<DiaryEntry>()) }

                getEntries { entries ->
                    diaryEntries = entries
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp),
                    topBar = {
                        TopAppBar(title = { Text("Diary") })
                    }
                ) { innerPadding ->
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(navController = navController, diaryEntries = diaryEntries, modifier = Modifier.padding(innerPadding))
                        }
                        composable("addEntry") {
                            DiaryEntryScreen(
                                onSave = { entry ->
                                    addEntry(entry)
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
    private fun addEntry(entry: DiaryEntry) {
        db.collection("entries")
            .add(entry)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document", e)
            }
    }

    private fun getEntries(callback: (List<DiaryEntry>) -> Unit) {
        db.collection("entries")
            .get()
            .addOnSuccessListener { result ->
                val entries = result.map { document -> document.toObject(DiaryEntry::class.java) }
                callback(entries)
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents.", exception)
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
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }
}
data class DiaryEntry(
    val title: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val location: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
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
            entry.location?.let {
                Text(
                    text = "Location: $it",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = "Date: ${formatDate(entry.timestamp)}",
            )
        }
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    val date = java.util.Date(timestamp)
    return sdf.format(date)
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
        // Przyciski do dodawania obrazu i nagrań audio
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
                        location = location?.let { "${it.latitude}, ${it.longitude}" }
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



