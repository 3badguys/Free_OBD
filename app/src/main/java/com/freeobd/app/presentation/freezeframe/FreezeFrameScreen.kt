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

package com.freeobd.app.presentation.freezeframe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeobd.app.presentation.components.DetailCard
import com.freeobd.app.presentation.components.PidDetailDialog
import com.freeobd.app.presentation.components.SupportBlockGrid
import com.freeobd.app.presentation.components.SupportLegend
import com.freeobd.app.presentation.theme.*
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreezeFrameScreen(
    onNavigateBack: () -> Unit,
    viewModel: FreezeFrameViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPidMetadata by viewModel.selectedPidMetadata.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.onEvent(FreezeFrameEvent.Load)
    }

    LaunchedEffect(uiState.scrollToPid) {
        val target = uiState.scrollToPid ?: return@LaunchedEffect
        val idx = uiState.pidStates.indexOfFirst { it.pidId == target }
        // 4 header items (frame + segments + discovery + title) before PID cards
        if (idx >= 0) listState.animateScrollToItem(4 + idx)
        viewModel.onScrollConsumed()
    }

    val ffSegments = (0..0xE0 step 0x20).toList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Freeze Frame") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Previous frame
                    IconButton(
                        onClick = { viewModel.onEvent(FreezeFrameEvent.PrevFrame) },
                        enabled = !uiState.isLoading && uiState.frameNumber > 0
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Frame",
                            tint = if (uiState.frameNumber > 0) Primary else OnSurfaceVariant)
                    }
                    // Next frame
                    IconButton(
                        onClick = { viewModel.onEvent(FreezeFrameEvent.NextFrame) },
                        enabled = !uiState.isLoading && uiState.hasMoreFrames
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Frame",
                            tint = if (uiState.hasMoreFrames) Primary else OnSurfaceVariant)
                    }
                    // Refresh
                    IconButton(
                        onClick = { viewModel.onEvent(FreezeFrameEvent.Load) },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh",
                            tint = if (uiState.isLoading) OnSurfaceVariant else Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface, titleContentColor = OnBackground
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.bitmapHex.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
                uiState.error != null -> {
                    val msg = uiState.error!!
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Cancel, null, Modifier.size(48.dp), tint = StatusRed)
                            Spacer(Modifier.height(16.dp))
                            Text(msg, color = OnBackground)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.onEvent(FreezeFrameEvent.Load) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                else -> FreezeFrameContent(uiState, listState, ffSegments, viewModel)
            }
        }
    }

    // PID detail dialog
    if (selectedPidMetadata != null) {
        PidDetailDialog(
            metadata = selectedPidMetadata!!,
            onDismiss = { viewModel.dismissPidDetail() }
        )
    }
}

@Composable
private fun FreezeFrameContent(
    state: FreezeFrameUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    ffSegments: List<Int>,
    viewModel: FreezeFrameViewModel
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Frame indicator
        item(key = "frame") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Frame #${state.frameNumber}", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = Primary)
                    Text(
                        String.format("02%02X%02X", state.segment, state.frameNumber),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = StatusYellow
                    )
                }
            }
        }

        // Segment selector
        item(key = "segments") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("PID Segment", style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ffSegments.forEachIndexed { _, seg ->
                            val label = String.format("%02X", seg)
                            val isSelected = seg == state.segment
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (!isSelected) viewModel.onEvent(
                                        FreezeFrameEvent.SelectSegment(seg)
                                    )
                                },
                                label = {
                                    Text(label, style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace)
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(alpha = 0.2f),
                                    selectedLabelColor = Primary
                                )
                            )
                        }
                    }
                }
            }
        }

        // Raw hex + blocks
        item(key = "discovery") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            String.format("02%02X%02X Response", state.segment, state.frameNumber),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = StatusYellow
                        )
                        Text(
                            "${state.supportedPids.size} supported",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.bitmapHex.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = OnBackground,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Support Status", style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    SupportBlockGrid(
                        items = state.pidStates,
                        idExtractor = { it.pidId },
                        supportedExtractor = { it.isSupported },
                        onBlockClick = { viewModel.onEvent(FreezeFrameEvent.ScrollToPid(it)) }
                    )
                    Spacer(Modifier.height(6.dp))
                    SupportLegend()
                }
            }
        }

        // Detail cards title
        item(key = "title") {
            Text("PID Details", style = MaterialTheme.typography.titleMedium, color = OnBackground)
        }

        itemsIndexed(state.pidStates, key = { _, s -> "pid_${s.pidId}" }) { _, ps ->
            DetailCard(
                command = String.format("02%02X%02X", ps.pidId, state.frameNumber),
                description = ps.description,
                isSupported = ps.isSupported,
                isLoading = ps.result is FreezeFramePidResult.Loading,
                resultText = (ps.result as? FreezeFramePidResult.Success)?.data,
                errorText = (ps.result as? FreezeFramePidResult.Error)?.message,
                onClick = { viewModel.onEvent(FreezeFrameEvent.ShowPidDetail(ps.pidId)) }
            )
        }

        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PidCard(state: FreezeFramePidState, frameNumber: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isSupported) Surface else SurfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = String.format("02%02X%02X", state.pidId, frameNumber),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = StatusYellow,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(state.description, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium, color = OnBackground)
                }
                Icon(
                    if (state.isSupported) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    null, Modifier.size(24.dp),
                    tint = if (state.isSupported) StatusGreen else StatusRed
                )
            }
            if (state.isSupported) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = SurfaceVariant, thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                when (val r = state.result) {
                    is FreezeFramePidResult.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Fetching…", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                    }
                    is FreezeFramePidResult.Success -> {
                        Text(r.data, style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace, color = OnBackground, lineHeight = 20.sp)
                    }
                    is FreezeFramePidResult.Error -> {
                        Text(r.message, style = MaterialTheme.typography.bodySmall, color = StatusYellow)
                    }
                }
            }
        }
    }
}
