package com.sam.lifelogger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Pure rendering composable — draws minimal waveform bars from normalized heights.
 * Bars are 50% of each slot width (bar + gap), rendered in primary theme color.
 *
 * @param barHeights  Normalized 0..1 values per bar. Empty list = nothing drawn.
 * @param primaryColor  Color for the bars. Defaults to a purple/blue that works in dark/light themes.
 * @param modifier  Standard compose modifier.
 */
@Composable
fun WaveformView(
    barHeights: List<Float>,
    primaryColor: Color = Color(0xFF6C63FF),
    modifier: Modifier = Modifier
) {
    if (barHeights.isEmpty()) return

    Canvas(modifier = modifier) {
        val barCount = barHeights.size
        val slotWidth = size.width / barCount
        val barWidth = slotWidth * 0.5f
        val gap = slotWidth * 0.5f

        barHeights.forEachIndexed { index, height ->
            // Clamp height to [0, 1], scale to canvas height
            val clamped = height.coerceIn(0f, 1f)
            val barHeight = clamped * size.height
            val x = index * slotWidth + gap / 2f

            // Skip drawing if bar is too small to see
            if (barHeight < 0.5f) return@forEachIndexed

            drawRect(
                color = primaryColor.copy(alpha = 0.4f + clamped * 0.3f),
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight)
            )
        }
    }
}
