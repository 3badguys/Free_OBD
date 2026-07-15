package com.freeobd.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeobd.app.presentation.theme.*

/**
 * Generic support-status block grid. 8 blocks per row, 4 rows = 32 items.
 *
 * Each item must provide an [id] (hex displayed above), a [isSupported] flag
 * (green = supported, red = unsupported), and optionally a [isClickable] flag
 * (only supported + clickable blocks respond to taps).
 *
 * @param items       The list of items to display (up to 32).
 * @param idExtractor Extracts the PID/InfoType ID for the hex label.
 * @param supportedExtractor Whether this item is in the bitmap.
 * @param clickableExtractor Whether this item responds to taps (default = supported).
 * @param onBlockClick Called with the ID when a supported block is tapped.
 */
@Composable
fun <T> SupportBlockGrid(
    items: List<T>,
    idExtractor: (T) -> Int,
    supportedExtractor: (T) -> Boolean,
    clickableExtractor: (T) -> Boolean = supportedExtractor,
    onBlockClick: (Int) -> Unit
) {
    val rows = items.chunked(8)
    rows.forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            rowItems.forEach { item ->
                val id = idExtractor(item)
                val isSupported = supportedExtractor(item)
                val canClick = clickableExtractor(item) && isSupported
                SupportBlock(
                    label = String.format("%02X", id),
                    isSupported = isSupported,
                    enabled = canClick,
                    onClick = { onBlockClick(id) },
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(8 - rowItems.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
fun SupportBlock(
    label: String,
    isSupported: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isSupported) StatusGreen.copy(alpha = 0.15f) else StatusRed.copy(alpha = 0.12f)
    val bd = if (isSupported) StatusGreen.copy(alpha = 0.5f) else StatusRed.copy(alpha = 0.4f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, bd, RoundedCornerShape(4.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = OnSurfaceVariant)
    }
}

/** Shared legend row: 🟩 Supported  🟥 Unsupported */
@Composable
fun SupportLegend(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(StatusGreen))
            Spacer(Modifier.width(4.dp))
            Text("Supported", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(StatusRed.copy(alpha = 0.6f)))
            Spacer(Modifier.width(4.dp))
            Text("Unsupported", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        }
    }
}

/**
 * Shared detail card for a single PID/InfoType item.
 *
 * @param command  The OBD command string (e.g. "010C", "020C00", "0902").
 * @param description Human-readable label.
 * @param isSupported Whether in the bitmap.
 * @param isLoading True while fetching.
 * @param resultText Display text on success, null while loading.
 * @param errorText Display text on error, null on success.
 * @param onClick Optional callback when the card is tapped (for PID detail dialog).
 */
@Composable
fun DetailCard(
    command: String,
    description: String,
    isSupported: Boolean,
    isLoading: Boolean,
    resultText: String?,
    errorText: String?,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick != null)
        Modifier.clickable(onClick = onClick)
    else Modifier

    Card(
        modifier = Modifier.fillMaxWidth().then(clickModifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isSupported) Surface else SurfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(command, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, color = StatusYellow,
                        style = MaterialTheme.typography.labelMedium)
                    Text(description, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium, color = OnBackground)
                }
                Icon(
                    if (isSupported) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    null, Modifier.size(24.dp),
                    tint = if (isSupported) StatusGreen else StatusRed
                )
            }
            if (isSupported) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = SurfaceVariant, thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                when {
                    isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Fetching…", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    resultText != null -> Text(resultText, style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace, color = OnBackground, lineHeight = 20.sp)
                    errorText != null -> Text(errorText, style = MaterialTheme.typography.bodySmall, color = StatusYellow)
                }
            }
        }
    }
}
