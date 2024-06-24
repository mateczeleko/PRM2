package com.example.prm2

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.location.Geocoder
import android.location.Location
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Locale

private var mediaRecorder: MediaRecorder? = null
private var mediaPlayer: MediaPlayer? = null
private var output: String? = null

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    private val db: FirebaseFirestore by lazy { Firebase.firestore }
    private val RADIUS = 100f
    private val locationHelper by lazy { LocationHelper(this) }


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PRM2Theme {
                val authenticated = remember { mutableStateOf(false) }
                val navController = rememberNavController()
                var diaryEntries by remember { mutableStateOf(mapOf<String, DiaryEntry>()) }
                fun refreshEntries() {
                    getEntries(db) { entries ->
                        diaryEntries = entries
                    }
                }

                LaunchedEffect(Unit) {
                    refreshEntries()
                }

                LaunchedEffect(locationHelper.currentLocation.value) {
                    println("AAAAALocation: ${locationHelper.currentLocation.value}")
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp),


                    topBar = {
                        if (authenticated.value)
                        TopAppBar(
                            title = {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    FloatingActionButton(
                                        onClick = { navController.navigate(route = "home") },
                                        content = { Icon(
                                            imageVector = Icons.Filled.Home,
                                            contentDescription = null
                                        ) },
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }


                        })
                    }
                ) { innerPadding ->
                    NavHost(navController = navController, startDestination = "pin") {
                        composable("pin") {
                            val pin = remember { mutableStateOf("") }
                            Column(modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Enter PIN")
                                TextField(value = pin.value, onValueChange = { pin.value = it})
                                Button(onClick = {
                                    if (pin.value == "1234") {
                                        authenticated.value = true
                                        navController.navigate("home")
                                    } else {
                                        Toast.makeText(this@MainActivity, "Invalid PIN", Toast.LENGTH_SHORT).show()
                                        pin.value = ""
                                    }
                                }) {
                                    Text("Submit")
                                }
                            }
                        }
                        composable("home") {
                            HomeScreen(
                                navController = navController,
                                diaryEntries = diaryEntries,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                        composable("addEntry") {
                            DiaryEntryScreen(
                                onSave = { entry ->
                                    addEntry(entry, db)
                                    refreshEntries()
                                    navController.navigate("home")
                                },
                                modifier = Modifier.padding(innerPadding),
                                getLocation = { callback ->
                                    getCurrentLocation(callback)
                                },
                                navController = navController
                            )
                        }
                        composable("entry/{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: ""
                            val entry = diaryEntries.get(id) ?: DiaryEntry()
                            DiaryEntryDetailScreen(
                                navController,
                                id,
                                entry,
                                modifier = Modifier.padding(innerPadding)
                            )

                        }
                        composable("editEntry/{id}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: ""
                            val entry = diaryEntries.get(id) ?: DiaryEntry()
                            DiaryEntryScreen(
                                entry = entry,
                                onSave = { updatedEntry ->
                                    updateEntry(id, updatedEntry, db)
                                    refreshEntries()
                                    navController.navigate("home")
                                },
                                modifier = Modifier.padding(innerPadding),
                                getLocation = { callback ->
                                    getCurrentLocation(callback)
                                },
                                navController = navController
                            )
                        }
                        composable("map") {
                            GoogleMapScreen(
                                modifier = Modifier.fillMaxSize(),
                                entries = diaryEntries
                            )
                        }
                        composable("imagePicker") {
                            ImagePickerAndEditor()
                        }
                    }
                }
            }
            Intent(this, LocationCheckService::class.java).also { intent ->
                startService(intent)
            }

        }

    }
    fun getCityName(lat: Double, long: Double): String {
        val geoCoder = Geocoder(this, Locale.getDefault())
        return geoCoder.getFromLocation(lat, long, 1)?.let { address ->
            return address[0].locality
        } ?: "unknown city"
    }


    @SuppressLint("MissingPermission")
    private fun getCurrentLocation(callback: (Location?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        val latLng = locationHelper.currentLocation.value
        val location = Location("")
        location.latitude = latLng.latitude
        location.longitude = latLng.longitude
        callback(location)
    }

    // implement onresume
    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        locationHelper.startLocationUpdates()
    }


    //implement onPause
    override fun onPause() {
        super.onPause()
        locationHelper.stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationHelper.stopLocationUpdates()
    }

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }

    @Composable
    fun DiaryEntryScreen(
        onSave: (DiaryEntry) -> Unit,
        modifier: Modifier,
        getLocation: (callback: (Location?) -> Unit) -> Unit,
        entry: DiaryEntry? = null,
        navController: NavController
    ) {

        var audioBlob by remember { mutableStateOf<Blob?>(null) }
        var title by remember { mutableStateOf(TextFieldValue(text = entry?.title ?: "")) }
        var content by remember { mutableStateOf(TextFieldValue(text = entry?.content ?: "")) }
        var imageUri = remember { mutableStateOf<Uri?>(null) }
        var audioUrl = remember { mutableStateOf<String?>(null) }
        var location by remember { mutableStateOf<Location?>(null) }
        val context = LocalContext.current
        var isRecording by remember { mutableStateOf(false) }
        Column(modifier = modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.new_diary_entry),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            TextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.title)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.content)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Button(onClick = {
                navController.navigate("imagePicker")

            }) {
                Text(stringResource(R.string.add_image))
            }
            Button(onClick = {
                if (isRecording) {
                    CoroutineScope(Dispatchers.IO).launch {
                        audioUrl.value = stopRecording(title.text)
                        isRecording = false
                    }

                } else {
                    startRecording(context)
                    isRecording = true
                }
            }) {
                Text(if (isRecording) stringResource(R.string.stop_recording) else stringResource(R.string.start_recording))
            }
            Button(
                onClick = {
                    getLocation { loc ->
                        location = loc
                        val entry = DiaryEntry(
                            title = title.text,
                            content = content.text,
                            imageUrl = imageUri.value?.toString(),
                            audioUrl = audioUrl.value,
                            location = location?.let { "${it.latitude}, ${it.longitude}" },
                            cityName = getCityName(
                                location?.latitude ?: 0.0,
                                location?.longitude ?: 0.0
                            ),
                        )
                        onSave(entry)
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    diaryEntries: Map<String, DiaryEntry>,
    modifier: Modifier
) {

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        TopAppBar(
            title = { Text("Diary Entries") },
            actions = {
                IconButton(onClick = { navController.navigate("map") }) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = null
                    )
                }
            }
        )


        LazyColumn() {
            items(diaryEntries.keys.toList()) { key ->
                DiaryEntryCard(diaryEntries.get(key)!!, navController, key)
            }

        }

    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        FloatingActionButton(
            modifier = Modifier.offset(x = -16.dp, y = -16.dp),
            onClick = { navController.navigate("addEntry") },
            content = {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(id = R.string.add_entry)
                )
            }
        )
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEntryDetailScreen(
    navController: NavController,
    id: String,
    entry: DiaryEntry,
    modifier: Modifier
) {
    Scaffold(modifier = modifier) {
        Column(modifier = Modifier.padding(40.dp)) {
            Text(text = "Title: ${entry.title}", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Content: ${entry.content}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            entry.imageUrl?.let {
                Text(text = "Image URL: $it", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }
            entry.audioUrl?.let {
                Text(text = "Audio URL: $it", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(text = "Location: ${entry.location}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "City: ${entry.cityName}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Timestamp: ${formatDate(entry.timestamp)}",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = {
                navController.navigate("editEntry/${id}")
            }) {
                Text("Edit")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEntryCard(entry: DiaryEntry, navController: NavController, id: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp), onClick = {
            navController.navigate("entry/${id}")
        }
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
            entry.imageUrl?.let {
                val imageBitmap = getBitmapFromUri(LocalContext.current, Uri.parse(it))
                Image(bitmap = imageBitmap.asImageBitmap(), contentDescription = null)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = "${formatDate(entry.timestamp)}",
            )
        }
    }
}

@Composable
fun ImagePickerAndEditor() {
    val context = LocalContext.current
    val imageUri = remember { mutableStateOf<Uri?>(null) }
    val textOnImage = remember { mutableStateOf("Your Text") }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri.value = uri
    }

    Column(
        modifier = Modifier.padding(top=200.dp),
    ) {
        Button(onClick = { launcher.launch("image/*") }) {
            Text("Pick an image")
        }
        Text(text = "Hello, World!")

        if (imageUri.value != null) {
            val bitmap = getBitmapFromUri(context, imageUri.value!!)
            val editedBitmap = addTextToBitmap(bitmap, textOnImage.value)
            Image(bitmap = editedBitmap.asImageBitmap(), contentDescription = null)
        } else {
            Text("No image selected yet")
        }
    }
}

fun getBitmapFromUri(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
    } else {
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}

fun addTextToBitmap(bitmap: Bitmap, text: String): Bitmap {
//    val copyBitmap = bitmap.copy(bitmap.config, false)
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)
    val paint = Paint().apply {
        color = Color.RED
        textSize = 50f
        style = Paint.Style.FILL
    }
    canvas.drawText(text, 50f, 50f, paint)
    return mutableBitmap
}

fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault())
    val date = java.util.Date(timestamp)
    return sdf.format(date)
}

fun startRecording(context: Context) {
    output = "${context.externalCacheDir?.absolutePath}/recording.3gp"
    Log.d("Recording", "Output file: $output")

    mediaRecorder = MediaRecorder().apply {
        try {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(output)

            prepare()
            start()
            Log.d("Recording", "Recording started")
        } catch (e: Exception) {
            Log.e("Recording", "Error starting recording", e)
        }
    }
}

@SuppressLint("RestrictedApi")
suspend fun stopRecording(title: String): String {
    mediaRecorder?.apply {
        stop()
        release()
    }
    mediaRecorder = null

    val bomba = title
    val storage = Firebase.storage
    val storageRef = storage.reference.child("audio/$bomba.mp4")
    delay(1000)
    val audioFile = File(output!!)
    val audioData = audioFile.readBytes()
    val audioBlob = Blob.fromBytes(audioData)
    Log.d("Audio data size", "${audioData.size}")
    val uploadTask = storageRef.putBytes(audioBlob.toBytes()).onSuccessTask { task ->
        task.storage.downloadUrl
    }
//    return uploadTask.snapshot.storage.downloadUrl.toString()
    return uploadTask.await().toString()
}








