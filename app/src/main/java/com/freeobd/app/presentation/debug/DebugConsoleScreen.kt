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

package com.freeobd.app.presentation.debug

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeobd.app.domain.model.DebugLog
import com.freeobd.app.presentation.theme.*
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsoleScreen(
    onNavigateBack: () -> Unit,
    viewModel: DebugViewModel = koinViewModel()
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var txInput by remember { mutableStateOf("") }

    // SAF file picker for save-as
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            val result = viewModel.saveToUri(context, it)
            val msg = result.fold(
                onSuccess = { "Saved." },
                onFailure = { "Save failed: ${it.message}" }
            )
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-scroll to bottom when new entries appear
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Console") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Text(
                        "${logs.size} entries",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    IconButton(
                        onClick = {
                            saveLauncher.launch("obd_debug.txt")
                        },
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save to file")
                    }
                    IconButton(
                        onClick = { viewModel.clear() },
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = OnBackground
                )
            )
        },
        bottomBar = {
            // Manual TX input bar
            Surface(
                color = Surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = txInput,
                        onValueChange = { txInput = it },
                        placeholder = { Text("AT or OBD command...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (txInput.isNotBlank()) {
                                    viewModel.sendCommand(txInput.trim())
                                    txInput = ""
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = OnSurfaceVariant,
                            cursorColor = Primary
                        )
                    )
                    IconButton(
                        onClick = {
                            if (txInput.isNotBlank()) {
                                viewModel.sendCommand(txInput.trim())
                                txInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Primary)
                    }
                }
            }
        }
    ) { paddingValues ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (viewModel.isEnabled)
                        "Logging is enabled.\nWaiting for ELM327 commands..."
                    else
                        "Debug logging is disabled.\nEnable it in Bluetooth screen → Advanced options.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceVariant
                )
            }
        } else {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(logs, key = { it.id }) { entry ->
                        LogEntry(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntry(entry: DebugLog) {
    val (textColor, bgColor) = when (entry.type) {
        DebugLog.Type.TX -> TxColor to TxBg
        DebugLog.Type.RX -> RxColor to RxBg
        DebugLog.Type.ERROR -> ErrorColor to ErrorBg
    }
    val prefix = when (entry.type) {
        DebugLog.Type.TX -> "TX"
        DebugLog.Type.RX -> "RX"
        DebugLog.Type.ERROR -> "ERR"
    }

    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = "$prefix  ${entry.message}",
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// Log entry colors
private val TxColor = androidx.compose.ui.graphics.Color(0xFF4FC3F7)
private val TxBg = androidx.compose.ui.graphics.Color(0x0A4FC3F7)
private val RxColor = androidx.compose.ui.graphics.Color(0xFFE0E0E0)
private val RxBg = androidx.compose.ui.graphics.Color(0x00000000)
private val ErrorColor = androidx.compose.ui.graphics.Color(0xFFEF5350)
private val ErrorBg = androidx.compose.ui.graphics.Color(0x0DEF5350)
