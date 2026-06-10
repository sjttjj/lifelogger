package com.sam.lifelogger.ui

import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

private const val BAR_COUNT = 32
private const val TAG = "RecordingPlayer"

/**
 * Inline expandable audio player.
 *
 * Manages MediaPlayer + Visualizer lifecycle via DisposableEffect.
 * Bar heights are computed from Visualizer waveform data and passed to WaveformView.
 *
 * @param file  The audio file to play.
 * @param modifier  Standard compose modifier.
 */
@Composable
fun RecordingPlayer(
    file: File,
    modifier: Modifier = Modifier
) {
    // --- State ---
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var barHeights by remember { mutableStateOf(List(BAR_COUNT) { 0f }) }

    // --- MediaPlayer + Visualizer lifecycle ---
    DisposableEffect(file.absolutePath) {
        val mp = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener {
                durationMs = it.duration
                it.start()
                isPlaying = true
            }
            setOnCompletionListener {
                isPlaying = false
                currentPositionMs = 0
                progress = 0f
                it.seekTo(0)
            }
            prepareAsync()
        }

        val viz = try {
            Visualizer(mp.audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (waveform == null || waveform.isEmpty()) return
                            val samplesPerBar = waveform.size / BAR_COUNT
                            val heights = FloatArray(BAR_COUNT) { i ->
                                val start = i * samplesPerBar
                                var sum = 0f
                                for (j in 0 until samplesPerBar.coerceAtMost(waveform.size - start)) {
                                    sum += abs(waveform[start + j].toInt()).toFloat()
                                }
                                (sum / samplesPerBar) / 128f // normalize to 0..1
                            }
                            barHeights = heights.toList()
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) = Unit
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,  // capture waveform
                    false  // don't capture FFT
                )
                enabled = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer not available", e)
            null
        }

        mediaPlayer = mp

        onDispose {
            try {
                viz?.enabled = false
                viz?.release()
            } catch (_: Exception) {}
            try {
                mp.stop()
            } catch (_: Exception) {}
            mp.release()
            mediaPlayer = null
        }
    }

    // --- Progress polling coroutine ---
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val mp = mediaPlayer ?: break
            try {
                currentPositionMs = mp.currentPosition
                if (durationMs > 0) {
                    progress = currentPositionMs.toFloat() / durationMs.toFloat()
                }
            } catch (_: Exception) {
                break
            }
            delay(200L)
        }
    }

    // --- UI ---
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause button
        FilledIconButton(
            onClick = {
                val mp = mediaPlayer ?: return@FilledIconButton
                if (isPlaying) {
                    mp.pause()
                    isPlaying = false
                } else {
                    mp.start()
                    isPlaying = true
                }
            },
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (isPlaying) {
                PauseIcon(
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Combined waveform + progress bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
        ) {
            // Waveform background
            WaveformView(
                barHeights = barHeights,
                primaryColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 4.dp)
            )

            // Progress slider (foreground)
            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    progress = newProgress
                },
                onValueChangeFinished = {
                    val mp = mediaPlayer ?: return@Slider
                    val targetMs = (progress * durationMs).toInt()
                    try {
                        mp.seekTo(targetMs)
                        currentPositionMs = targetMs
                    } catch (_: Exception) {}
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.matchParentSize()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Time display
        Text(
            text = "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

@Composable
private fun PauseIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val color = if (tint == Color.Unspecified) Color.Black else tint
    Canvas(modifier = modifier) {
        val barWidth = size.width * 0.3f
        val gap = size.width * 0.1f
        val leftBarX = (size.width - 2 * barWidth - gap) / 2f
        val rightBarX = leftBarX + barWidth + gap
        drawRect(
            color = color,
            topLeft = Offset(leftBarX, 0f),
            size = Size(barWidth, size.height)
        )
        drawRect(
            color = color,
            topLeft = Offset(rightBarX, 0f),
            size = Size(barWidth, size.height)
        )
    }
}
