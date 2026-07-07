package com.freeobd.app.presentation.vehicle

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Refresh
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
import com.freeobd.app.presentation.theme.*
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Vehicle Information screen — Mode 09 InfoType discovery and display.
 *
 * Layout:
 *   1. Toolbar with title + refresh button
 *   2. 0900 raw hex response
 *   3. Support status blocks (green = supported, red = unsupported)
 *   4. Per-InfoType detail cards (click green block → scroll to card)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    onNavigateBack: () -> Unit,
    viewModel: VehicleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.onEvent(VehicleEvent.Load)
    }

    // Consume scroll-to event
    LaunchedEffect(uiState.scrollToInfoType) {
        val target = uiState.scrollToInfoType ?: return@LaunchedEffect
        // Find the index of the target type within the type states list
        val idx = uiState.typeStates.indexOfFirst { it.meta.infoType == target }
        if (idx >= 0) {
            // Offset: header items (hex card + support blocks + section title)
            val headerCount = 3 // RawHexCard + SupportBlocks
            listState.animateScrollToItem(headerCount + idx)
        }
        viewModel.onScrollConsumed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Info") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onEvent(VehicleEvent.Load) },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (uiState.isLoading) OnSurfaceVariant else Primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = OnBackground
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.bitmapHex.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                }

                uiState.error != null -> {
                    val errorMsg = uiState.error!!
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Cancel, contentDescription = null,
                                modifier = Modifier.size(48.dp), tint = StatusRed
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(errorMsg, color = OnBackground)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.onEvent(VehicleEvent.Load) }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                else -> {
                    VehicleInfoContent(
                        state = uiState,
                        listState = listState,
                        onBlockClick = { infoType ->
                            viewModel.onEvent(VehicleEvent.ScrollToType(infoType))
                        }
                    )
                }
            }
        }
    }
}

// ── Main content ──────────────────────────────────────────

@Composable
private fun VehicleInfoContent(
    state: VehicleUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onBlockClick: (Int) -> Unit
) {
    // Show all InfoTypes in detail cards
    val detailItems = state.typeStates

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: raw hex card
        item(key = "hex") {
            RawHexCard(hex = state.bitmapHex)
        }

        // Header: support blocks
        item(key = "blocks") {
            SupportBlocks(typeStates = state.typeStates, onBlockClick = onBlockClick)
        }

        // Section title
        item(key = "title") {
            Text(
                "InfoType Details",
                style = MaterialTheme.typography.titleMedium,
                color = OnBackground
            )
        }

        // Detail cards
        itemsIndexed(detailItems, key = { _, it -> "type_${it.meta.infoType}" }) { _, typeState ->
            InfoTypeCard(typeState)
        }

        item(key = "spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Raw hex card ──────────────────────────────────────────

@Composable
private fun RawHexCard(hex: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("0900 Response",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = StatusYellow)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hex.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = OnBackground
            )
        }
    }
}

// ── Support blocks ────────────────────────────────────────

@Composable
private fun SupportBlocks(
    typeStates: List<VehicleInfoTypeState>,
    onBlockClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Support Status", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // 8 blocks per row, 4 rows for InfoTypes 01–20
            val rows = typeStates.chunked(8)
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowItems.forEach { state ->
                        SupportBlock(
                            label = String.format("%02X", state.meta.infoType),
                            isSupported = state.isSupported,
                            enabled = state.isSupported,
                            onClick = {
                                if (state.isSupported) {
                                    onBlockClick(state.meta.infoType)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(8 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(StatusGreen)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Supported", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(StatusRed.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Unsupported", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SupportBlock(
    label: String,
    isSupported: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSupported) StatusGreen.copy(alpha = 0.15f) else StatusRed.copy(alpha = 0.12f)
    val borderColor = if (isSupported) StatusGreen.copy(alpha = 0.5f) else StatusRed.copy(alpha = 0.4f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(6.dp))
            .then(
                if (isSupported && enabled) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = OnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isSupported) "✓" else "✗",
            fontSize = 14.sp,
            color = if (isSupported) StatusGreen else StatusRed.copy(alpha = 0.6f)
        )
    }
}

// ── InfoType detail card ──────────────────────────────────

@Composable
private fun InfoTypeCard(state: VehicleInfoTypeState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isSupported) Surface else SurfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: command + description + status icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.meta.command,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = StatusYellow
                    )
                    Text(
                        text = state.meta.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = OnBackground
                    )
                }
                Icon(
                    imageVector = if (state.isSupported) Icons.Default.CheckCircle
                    else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (state.isSupported) StatusGreen else StatusRed,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Result (only for supported types)
            if (state.isSupported) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = SurfaceVariant, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                when (val result = state.result) {
                    is VehicleInfoTypeResult.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetching…", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                    }
                    is VehicleInfoTypeResult.Success -> {
                        Text(
                            text = result.data,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = OnBackground,
                            lineHeight = 20.sp
                        )
                    }
                    is VehicleInfoTypeResult.Error -> {
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusYellow
                        )
                    }
                }
            }
        }
    }
}
