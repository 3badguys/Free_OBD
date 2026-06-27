package com.freeobd.app.presentation.dtc

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeobd.app.domain.model.DTC
import com.freeobd.app.domain.model.DTCSeverity
import com.freeobd.app.presentation.theme.*
import org.koin.androidx.compose.koinViewModel

/**
 * Diagnostic Trouble Codes screen with tabbed view (Stored / Pending / Permanent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtcScreen(
    onNavigateBack: () -> Unit,
    viewModel: DtcViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDtc by viewModel.selectedDtc.collectAsState()

    // Load codes on first composition
    LaunchedEffect(Unit) {
        viewModel.onEvent(DtcEvent.LoadCodes)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostic Codes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is DtcUiState.Loaded && (uiState as DtcUiState.Loaded).storedDTCs.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onEvent(DtcEvent.ClearCodes) }
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = "Clear Codes",
                                tint = StatusRed
                            )
                        }
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
            when (val state = uiState) {
                DtcUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                }

                is DtcUiState.Loaded -> {
                    DtcTabsContent(
                        state = state,
                        onTabSelected = { tab ->
                            viewModel.onEvent(DtcEvent.SelectTab(tab))
                        },
                        onDtcClick = { dtc ->
                            viewModel.onEvent(DtcEvent.ShowDetail(dtc))
                        },
                        onClearCodes = {
                            viewModel.onEvent(DtcEvent.ClearCodes)
                        }
                    )
                }

                DtcUiState.NoCodes -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = StatusGreen
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No Diagnostic Codes",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Your vehicle has no stored, pending, or permanent DTCs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurface
                            )
                        }
                    }
                }

                DtcUiState.Cleared -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = StatusGreen
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Codes Cleared",
                                style = MaterialTheme.typography.titleMedium,
                                color = OnBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.onEvent(DtcEvent.LoadCodes) }
                            ) {
                                Text("Re-read Codes")
                            }
                        }
                    }
                }

                is DtcUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = StatusRed
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(state.message, color = OnBackground)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.onEvent(DtcEvent.LoadCodes) }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }

    // DTC detail dialog
    if (selectedDtc != null) {
        DtcDetailDialog(
            dtc = selectedDtc!!,
            onDismiss = { viewModel.clearSelectedDtc() }
        )
    }
}

@Composable
private fun DtcTabsContent(
    state: DtcUiState.Loaded,
    onTabSelected: (DtcTab) -> Unit,
    onDtcClick: (DTC) -> Unit,
    onClearCodes: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab bar
        TabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            containerColor = Surface,
            contentColor = Primary
        ) {
            DtcTab.entries.forEach { tab ->
                val count = when (tab) {
                    DtcTab.STORED -> state.storedDTCs.size
                    DtcTab.PENDING -> state.pendingDTCs.size
                    DtcTab.PERMANENT -> state.permanentDTCs.size
                }
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${tab.label} ")
                            if (count > 0) {
                                Badge(
                                    containerColor = if (tab == state.selectedTab) Primary else SurfaceVariant
                                ) {
                                    Text("$count")
                                }
                            }
                        }
                    }
                )
            }
        }

        // DTC list for the selected tab
        val dtcs = when (state.selectedTab) {
            DtcTab.STORED -> state.storedDTCs
            DtcTab.PENDING -> state.pendingDTCs
            DtcTab.PERMANENT -> state.permanentDTCs
        }

        if (dtcs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No ${state.selectedTab.label.lowercase()} codes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurface
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(dtcs, key = { it.code }) { dtc ->
                    DtcListItem(dtc = dtc, onClick = { onDtcClick(dtc) })
                }
            }
        }
    }
}

@Composable
private fun DtcListItem(
    dtc: DTC,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Severity indicator
            Surface(
                modifier = Modifier.size(8.dp),
                shape = MaterialTheme.shapes.small,
                color = when (dtc.severity) {
                    DTCSeverity.LOW -> DtcLow
                    DTCSeverity.MEDIUM -> DtcMedium
                    DTCSeverity.HIGH -> DtcHigh
                    DTCSeverity.CRITICAL -> DtcCritical
                }
            ) {}

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dtc.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnBackground
                )
                Text(
                    text = dtc.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface,
                    maxLines = 2
                )
                if (dtc.system != null) {
                    Text(
                        text = dtc.system,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = OnSurfaceVariant
            )
        }
    }
}
