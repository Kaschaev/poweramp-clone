package com.player.app

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlin.math.cos
import kotlin.math.sin

data class AudioTrack(val title: String, val artist: String, val uri: Uri)

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}

class MainActivity : ComponentActivity() {
    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
        }, MoreExecutors.directExecutor())

        setContent {
            PowerampThemeApp(controller)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller?.release()
    }
}

@Composable
fun PowerampThemeApp(controller: MediaController?) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(0) }
    var tracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var currentTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) tracks = loadDeviceMusic(context)
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            Surface(color = Color(0xFF141414), tonalElevation = 6.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentScreen = 3 }) {
                        Icon(Icons.Default.GridView, contentDescription = null, tint = if (currentScreen == 3) Color.White else Color.Gray)
                    }
                    IconButton(onClick = { currentScreen = 1 }) {
                        Icon(Icons.Default.Equalizer, contentDescription = null, tint = if (currentScreen == 1) Color.White else Color.Gray)
                    }
                    IconButton(onClick = { currentScreen = 2 }) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = if (currentScreen == 2) Color.White else Color.Gray)
                    }
                    IconButton(onClick = { currentScreen = 0 }) {
                        Icon(Icons.Default.QueueMusic, contentDescription = null, tint = if (currentScreen == 0) Color.White else Color.Gray)
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            when (currentScreen) {
                0 -> PlayerMainScreen(currentTrack, isPlaying, controller)
                1 -> EqualizerScreen()
                2 -> KnobsEffectsScreen()
                3 -> LibraryScreen(tracks, onTrackSelect = { track ->
                    currentTrack = track
                    controller?.let { player ->
                        player.clearMediaItems()
                        player.setMediaItem(MediaItem.fromUri(track.uri))
                        player.prepare()
                        player.play()
                    }
                    currentScreen = 0
                }, onRequestPermission = {
                    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                    permissionLauncher.launch(perm)
                })
            }
        }
    }
}

@Composable
fun PlayerMainScreen(track: AudioTrack?, isPlaying: Boolean, controller: MediaController?) {
    var progress by remember { mutableFloatStateOf(0.35f) }
    val waveAmplitudes = remember { List(45) { (0.15f + Math.random().toFloat() * 0.85f) } }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF101010), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFF262626), modifier = Modifier.size(100.dp))
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = track?.title ?: "Been A While", color = Color.White, fontSize = 22.sp, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = track?.artist ?: "Wiley", color = Color.Gray, fontSize = 16.sp)
        }

        WaveformBar(amplitudes = waveAmplitudes, progress = progress, onSeek = { progress = it })

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { controller?.seekToPrevious() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.width(28.dp))
            Box(
                modifier = Modifier.size(68.dp).background(Color.White, CircleShape).clickable {
                    if (isPlaying) controller?.pause() else controller?.play()
                },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(modifier = Modifier.width(28.dp))
            IconButton(onClick = { controller?.seekToNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
fun EqualizerScreen() {
    val bands = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K")
    val gains = remember { mutableStateListOf(-3.2f, -1.1f, -1.4f, -1.4f, -0.8f, -0.8f, 1.8f, 0.0f) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("ЭКВАЛАЙЗЕР", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            bands.forEachIndexed { i, band ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    VerticalSlider(
                        value = gains[i],
                        onValueChange = { gains[i] = it },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(band, color = Color.Gray, fontSize = 11.sp)
                    Text("%.1f".format(gains[i]), color = Color(0xFFD4E157), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun KnobsEffectsScreen() {
    var volume by remember { mutableFloatStateOf(0.73f) }
    var balance by remember { mutableFloatStateOf(0.5f) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        Text("ЗВУКОВЫЕ ЭФФЕКТЫ", color = Color.White, fontSize = 18.sp)
        RotaryDial(value = balance, onValueChange = { balance = it }, label = "Баланс", textValue = "0,00")
        RotaryDial(value = volume, onValueChange = { volume = it }, label = "Громкость", textValue = "${(volume * 100).toInt()}%", showRing = true)
    }
}

@Composable
fun LibraryScreen(tracks: List<AudioTrack>, onTrackSelect: (AudioTrack) -> Unit, onRequestPermission: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262626)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Text("Сканировать треки устройства", color = Color.White)
        }
        LazyColumn {
            items(tracks) { track ->
                ListItem(
                    headlineContent = { Text(track.title, color = Color.White) },
                    supportingContent = { Text(track.artist, color = Color.Gray) },
                    colors = ListItemDefaults.colors(containerColor = Color.Black),
                    modifier = Modifier.clickable { onTrackSelect(track) }
                )
                HorizontalDivider(color = Color(0xFF1E1E1E))
            }
        }
    }
}

@Composable
fun RotaryDial(value: Float, onValueChange: (Float) -> Unit, label: String, textValue: String, showRing: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(130.dp).pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = -dragAmount.y * 0.005f
                    onValueChange((value + delta).coerceIn(0f, 1f))
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 - 8.dp.toPx()
                drawCircle(color = Color(0xFF1C1C1C), radius = radius)
                if (showRing) {
                    drawArc(
                        color = Color(0xFFD4E157),
                        startAngle = 135f,
                        sweepAngle = 270f * value,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                val angle = Math.toRadians((135.0 + 270.0 * value)).toFloat()
                val markerX = center.x + (radius - 14.dp.toPx()) * cos(angle)
                val markerY = center.y + (radius - 14.dp.toPx()) * sin(angle)
                drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(markerX, markerY))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(textValue, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun WaveformBar(amplitudes: List<Float>, progress: Float, onSeek: (Float) -> Unit) {
    Canvas(
        modifier = Modifier.fillMaxWidth().height(65.dp).pointerInput(Unit) {
            detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) }
        }
    ) {
        val count = amplitudes.size
        val barWidth = size.width / (count * 1.5f)
        val gap = barWidth * 0.5f

        for (i in 0 until count) {
            val left = i * (barWidth + gap)
            val barHeight = size.height * amplitudes[i].coerceAtLeast(0.1f)
            val top = (size.height - barHeight) / 2
            val isPlayed = (left / size.width) <= progress
            val color = if (isPlayed) Color.White else Color(0xFF333333)

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

@Composable
fun VerticalSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxHeight().width(24.dp).pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                val delta = -dragAmount.y * 0.1f
                onValueChange((value + delta).coerceIn(-10f, 10f))
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxHeight().width(6.dp)) {
            drawRoundRect(color = Color(0xFF222222), cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()))
            val normalized = (value + 10f) / 20f
            val knobY = size.height * (1f - normalized)
            drawCircle(color = Color(0xFFD4E157), radius = 6.dp.toPx(), center = Offset(size.width / 2, knobY))
        }
    }
}

fun loadDeviceMusic(context: Context): List<AudioTrack> {
    val list = mutableListOf<AudioTrack>()
    val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            list.add(AudioTrack(cursor.getString(titleCol), cursor.getString(artistCol), uri))
        }
    }
    return list
}
