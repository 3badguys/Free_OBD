/*
 * Copyright 2026 3badguys <chuiC456@163.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.freeobd.app.presentation.dtc

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.freeobd.app.domain.model.DTC
import com.freeobd.app.presentation.theme.*

/**
 * Dialog displaying detailed information about a single Diagnostic Trouble Code.
 */
@Composable
fun DtcDetailDialog(
    dtc: DTC,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = StatusYellow
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = dtc.code,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                // Description
                Text(
                    text = dtc.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Metadata rows
                DetailRow("Category", dtc.category.name)
                DetailRow("Status", dtc.status.name)

                Spacer(modifier = Modifier.height(8.dp))

                // Status badge
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when (dtc.status) {
                        com.freeobd.app.domain.model.DTCStatus.STORED ->
                            StatusYellow.copy(alpha = 0.2f)
                        com.freeobd.app.domain.model.DTCStatus.PENDING ->
                            StatusBlue.copy(alpha = 0.2f)
                        com.freeobd.app.domain.model.DTCStatus.PERMANENT ->
                            StatusRed.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = when (dtc.status) {
                            com.freeobd.app.domain.model.DTCStatus.STORED ->
                                "⚠ This code is stored and will keep the MIL on until the issue is resolved and codes are cleared."
                            com.freeobd.app.domain.model.DTCStatus.PENDING ->
                                "⚠ This code is pending — it has been detected but the MIL is not yet illuminated."
                            com.freeobd.app.domain.model.DTCStatus.PERMANENT ->
                                "⚠ This code is permanent and CANNOT be cleared via OBD. The underlying fault must be repaired first."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
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
