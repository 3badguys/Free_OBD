package com.freeobd.app.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freeobd.app.data.local.entity.PidMetadataEntity
import com.freeobd.app.presentation.theme.*

/**
 * Dialog displaying detailed PID metadata from pid_definitions.json.
 *
 * Shows all available fields: description, PID hex, mode, unit,
 * min/max range, bytes count, and formula (with proper line breaks).
 */
@Composable
fun PidDetailDialog(
    metadata: PidMetadataEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = StatusBlue
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = String.format("PID 0x%02X", metadata.pidId),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Description
                Text(
                    text = metadata.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnBackground
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Unit (if non-empty)
                if (metadata.unit.isNotEmpty()) {
                    DetailRow("Unit", metadata.unit)
                }

                // Range (if non-trivial)
                if (metadata.minValue != 0.0 || metadata.maxValue != 0.0) {
                    val rangeText = if (metadata.minValue == metadata.maxValue)
                        metadata.minValue.toCleanString()
                    else
                        "${metadata.minValue.toCleanString()} – ${metadata.maxValue.toCleanString()}"
                    DetailRow("Range", rangeText)
                }

                // Bytes count
                DetailRow("Bytes", "${metadata.bytesCount} byte(s)")

                // Formula (if non-empty)
                if (metadata.formula.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Formula",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Replace literal \n with actual newlines for proper display
                    val displayFormula = metadata.formula.replace("\\n", "\n")
                    Surface(
                        color = SurfaceVariant.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = displayFormula,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = OnSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = Surface
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = OnBackground,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Remove trailing zeros for cleaner numeric display (e.g. 16383.75 stays, 0.0 → 0). */
private fun Double.toCleanString(): String {
    return if (this == this.toLong().toDouble() && !this.isInfinite())
        this.toLong().toString()
    else
        this.toString().trimEnd('0').trimEnd('.')
}
