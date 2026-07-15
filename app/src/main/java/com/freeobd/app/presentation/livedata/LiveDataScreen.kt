package com.freeobd.app.presentation.livedata

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
fun LiveDataScreen(
    onNavigateBack: () -> Unit,
    viewModel: LiveDataViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPidMetadata by viewModel.selectedPidMetadata.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.onEvent(LiveDataEvent.Load) }

    LaunchedEffect(uiState.scrollToPid) {
        val target = uiState.scrollToPid ?: return@LaunchedEffect
        val idx = uiState.pidStates.indexOfFirst { it.pidId == target }
        // 3 header items: segments + discovery + title before PID cards
        if (idx >= 0) listState.animateScrollToItem(3 + idx)
        viewModel.onScrollConsumed()
    }

    val segments = (0..0xE0 step 0x20).toList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Data") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onEvent(LiveDataEvent.Load) },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh",
                            tint = if (uiState.isLoading) OnSurfaceVariant else Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface, titleContentColor = OnBackground)
            )
        }
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading && uiState.bitmapHex.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                uiState.error != null -> {
                    val msg = uiState.error!!
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Cancel, null, Modifier.size(48.dp), tint = StatusRed)
                            Spacer(Modifier.height(16.dp)); Text(msg, color = OnBackground)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.onEvent(LiveDataEvent.Load) }) { Text("Retry") }
                        }
                    }
                }
                else -> LiveDataContent(uiState, listState, segments, viewModel)
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
private fun LiveDataContent(
    state: LiveDataUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    segments: List<Int>,
    viewModel: LiveDataViewModel
) {
    LazyColumn(
        state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Segment selector
        item(key = "segments") {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface)) {
                Column(Modifier.padding(12.dp)) {
                    Text("PID Segment", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        segments.forEach { seg ->
                            val sel = seg == state.segment
                            FilterChip(selected = sel,
                                onClick = { if (!sel) viewModel.onEvent(LiveDataEvent.SelectSegment(seg)) },
                                label = { Text(String.format("%02X", seg), style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(alpha = 0.2f), selectedLabelColor = Primary))
                        }
                    }
                }
            }
        }

        // Raw hex + blocks
        item(key = "discovery") {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(String.format("01%02X Response", state.segment),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = StatusYellow)
                        Text("${state.supportedPids.size} supported", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(state.bitmapHex.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace, color = OnBackground, lineHeight = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Support Status", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    SupportBlockGrid(
                        items = state.pidStates,
                        idExtractor = { it.pidId },
                        supportedExtractor = { it.isSupported },
                        onBlockClick = { viewModel.onEvent(LiveDataEvent.ScrollToPid(it)) }
                    )
                    Spacer(Modifier.height(6.dp))
                    SupportLegend()
                }
            }
        }

        item(key = "title") { Text("PID Details", style = MaterialTheme.typography.titleMedium, color = OnBackground) }

        itemsIndexed(state.pidStates, key = { _, s -> "pid_${s.pidId}" }) { _, ps ->
            DetailCard(
                command = String.format("01%02X", ps.pidId),
                description = ps.description,
                isSupported = ps.isSupported,
                isLoading = ps.result is LiveDataPidResult.Loading,
                resultText = (ps.result as? LiveDataPidResult.Success)?.data,
                errorText = (ps.result as? LiveDataPidResult.Error)?.message,
                onClick = { viewModel.onEvent(LiveDataEvent.ShowPidDetail(ps.pidId)) }
            )
        }

        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PidCard(state: LiveDataPidState) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = if (state.isSupported) Surface else SurfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(String.format("01%02X", state.pidId), fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, color = StatusYellow, style = MaterialTheme.typography.labelMedium)
                    Text(state.description, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium, color = OnBackground)
                }
                Icon(if (state.isSupported) Icons.Default.CheckCircle else Icons.Default.Cancel, null,
                    Modifier.size(24.dp), tint = if (state.isSupported) StatusGreen else StatusRed)
            }
            if (state.isSupported) {
                Spacer(Modifier.height(8.dp)); HorizontalDivider(color = SurfaceVariant, thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                when (val r = state.result) {
                    is LiveDataPidResult.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Fetching…", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    is LiveDataPidResult.Success -> Text(r.data, style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace, color = OnBackground, lineHeight = 20.sp)
                    is LiveDataPidResult.Error -> Text(r.message, style = MaterialTheme.typography.bodySmall, color = StatusYellow)
                }
            }
        }
    }
}
