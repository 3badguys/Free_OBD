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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeobd.app.presentation.theme.*
import kotlin.math.*

/**
 * Automotive-style gauge widget with semi-circular arc.
 *
 * Features:
 * - Gradient arc (green → yellow → red) based on value position
 * - Dark center hub with tick marks
 * - Needle indicator with shadow
 * - Min/max edge labels
 */
@Composable
fun GaugeWidget(
    value: Double,
    label: String,
    unit: String,
    minValue: Double = 0.0,
    maxValue: Double = 100.0,
    modifier: Modifier = Modifier
) {
    val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0.0, 1.0)

    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize(0.82f)) {
                val w = size.width
                val h = size.height
                val strokeW = w * 0.095f
                val arcTopLeft = Offset(strokeW / 2, strokeW / 2)
                val arcSize = Size(w - strokeW, h * 2 - strokeW)
                val centerX = w / 2
                val centerY = h * 0.86f
                val radius = arcSize.width / 2

                // Background track
                drawArc(
                    color = GaugeArcBackground,
                    startAngle = 180f, sweepAngle = 180f,
                    useCenter = false, topLeft = arcTopLeft, size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )

                // Active arc with gradient
                val sweep = (fraction * 180f).toFloat()
                if (sweep > 0f) {
                    val gradientBrush = Brush.sweepGradient(
                        colors = listOf(StatusGreen, StatusYellow, StatusRed),
                        center = Offset(centerX, centerY)
                    )
                    drawArc(
                        brush = gradientBrush,
                        startAngle = 180f, sweepAngle = sweep,
                        useCenter = false, topLeft = arcTopLeft, size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }

                // Tick marks with labels
                val tickCount = 5
                for (i in 0..tickCount) {
                    val angle = Math.toRadians(180.0 + 180.0 * i / tickCount)
                    val cosA = cos(angle).toFloat()
                    val sinA = sin(angle).toFloat()
                    val tickInner = radius - strokeW / 2
                    val tickOuter = tickInner - w * 0.07f

                    drawLine(
                        color = GaugeTick,
                        start = Offset(centerX + tickInner * cosA, centerY + tickInner * sinA),
                        end = Offset(centerX + tickOuter * cosA, centerY + tickOuter * sinA),
                        strokeWidth = 2f
                    )
                }

                // Center hub (dark circle behind needle)
                drawCircle(
                    color = Color(0xFF1A1A2E),
                    radius = w * 0.09f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = GaugeNeedle,
                    radius = w * 0.04f,
                    center = Offset(centerX, centerY)
                )

                // Needle
                val needleAngle = Math.toRadians(180.0 + fraction * 180.0)
                val needleLen = radius * 0.55f
                val nx = centerX + needleLen * cos(needleAngle).toFloat()
                val ny = centerY + needleLen * sin(needleAngle).toFloat()

                // Needle shadow
                drawLine(
                    color = Color.Black.copy(alpha = 0.3f),
                    start = Offset(centerX + 1.dp.toPx(), centerY + 1.dp.toPx()),
                    end = Offset(nx + 1.dp.toPx(), ny + 1.dp.toPx()),
                    strokeWidth = 3f, cap = StrokeCap.Round
                )
                drawLine(
                    color = GaugeNeedle,
                    start = Offset(centerX, centerY),
                    end = Offset(nx, ny),
                    strokeWidth = 2.5f, cap = StrokeCap.Round
                )
            }

            // Center value + unit
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatValue(value),
                    style = MaterialTheme.typography.headlineSmall,
                    color = OnBackground
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
        }

        // Label
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = OnSurface, maxLines = 1)
    }
}

private fun formatValue(value: Double): String = when {
    value >= 1000 -> String.format("%.0f", value)
    value >= 100 -> String.format("%.0f", value)
    else -> String.format("%.1f", value)
}
