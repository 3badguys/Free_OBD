package com.freeobd.app.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeobd.app.domain.model.OBDData
import com.freeobd.app.presentation.theme.*
import kotlin.math.*

/**
 * Custom Canvas-based automotive gauge widget.
 *
 * Draws a semi-circular arc with:
 * - Background track (grey arc)
 * - Active value arc (teal)
 * - Tick marks at regular intervals
 * - Needle indicator
 * - Center value label
 * - Bottom label with PID name and unit
 *
 * @param value Current numeric value to display.
 * @param label PID name label (e.g. "RPM").
 * @param unit Unit string (e.g. "rpm").
 * @param minValue Minimum scale value.
 * @param maxValue Maximum scale value.
 * @param sizeFraction Fraction of available width to use (0.0-1.0).
 */
@Composable
fun GaugeWidget(
    value: Double,
    label: String,
    unit: String,
    minValue: Double = 0.0,
    maxValue: Double = 100.0,
    modifier: Modifier = Modifier,
    sizeFraction: Float = 0.85f
) {
    val arcColor = when {
        value > maxValue * 0.9 -> StatusRed
        value > maxValue * 0.75 -> StatusYellow
        else -> GaugeArc
    }

    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize(sizeFraction)) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val strokeWidth = canvasWidth * 0.10f
                val arcSize = Size(
                    canvasWidth - strokeWidth,
                    canvasHeight * 2 - strokeWidth
                )

                // Draw background arc (180° sweep)
                drawArc(
                    color = GaugeArcBackground,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Draw active value arc
                val sweepAngle = ((value - minValue) / (maxValue - minValue))
                    .coerceIn(0.0, 1.0)
                    .toFloat() * 180f

                drawArc(
                    color = arcColor,
                    startAngle = 180f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Draw tick marks
                val tickCount = 8
                val centerX = canvasWidth / 2
                val centerY = canvasHeight * 0.85f
                val radius = arcSize.width / 2
                val tickLength = canvasWidth * 0.08f

                for (i in 0..tickCount) {
                    val angle = Math.toRadians((180.0 + (180.0 * i / tickCount)))
                    val cos = cos(angle).toFloat()
                    val sin = sin(angle).toFloat()

                    val startX = centerX + (radius - strokeWidth / 2) * cos
                    val startY = centerY + (radius - strokeWidth / 2) * sin
                    val endX = centerX + (radius - strokeWidth / 2 - tickLength) * cos
                    val endY = centerY + (radius - strokeWidth / 2 - tickLength) * sin

                    drawLine(
                        color = GaugeTick,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 1.5f
                    )
                }

                // Draw needle
                val needleAngle = Math.toRadians(
                    (180.0 + ((value - minValue) / (maxValue - minValue)).coerceIn(0.0, 1.0) * 180.0)
                )
                val needleLength = radius * 0.65f
                val needleX = centerX + needleLength * cos(needleAngle).toFloat()
                val needleY = centerY + needleLength * sin(needleAngle).toFloat()

                rotate(180f, pivot = Offset(centerX, centerY)) {
                    // Pivot dot
                    drawCircle(
                        color = GaugeNeedle,
                        radius = canvasWidth * 0.04f,
                        center = Offset(centerX, centerY)
                    )
                }

                drawLine(
                    color = GaugeNeedle,
                    start = Offset(centerX, centerY),
                    end = Offset(needleX, needleY),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }

            // Center value text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatValue(value),
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnBackground
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
        }

        // Bottom label
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurface,
            maxLines = 1
        )
    }
}

/**
 * Format a numeric value for gauge display.
 * Uses integer display for values >= 100 or fractional display otherwise.
 */
private fun formatValue(value: Double): String {
    return when {
        value >= 1000 -> String.format("%.0f", value)
        value >= 100 -> String.format("%.0f", value)
        value >= 10 -> String.format("%.1f", value)
        else -> String.format("%.1f", value)
    }
}
