package com.freeobd.app.presentation.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeobd.app.domain.model.VehicleInfo
import com.freeobd.app.presentation.theme.*
import org.koin.androidx.compose.koinViewModel

/**
 * Vehicle Information screen — displays VIN, calibration IDs, and CVN.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(
    onNavigateBack: () -> Unit,
    viewModel: VehicleViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onEvent(VehicleEvent.Load)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Info") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                VehicleUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                }

                is VehicleUiState.Success -> {
                    VehicleInfoContent(state.info)
                }

                is VehicleUiState.Error -> {
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
                                onClick = { viewModel.onEvent(VehicleEvent.Load) }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleInfoContent(info: VehicleInfo) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // VIN card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = Primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "VIN",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnBackground
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (info.vin != null) {
                    Text(
                        text = info.vin,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnBackground
                    )
                } else {
                    Text(
                        text = "VIN not available from this ECU",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            }
        }

        // Calibration IDs
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        tint = StatusBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Calibration IDs",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnBackground
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (info.calibrationIds.isNotEmpty()) {
                    info.calibrationIds.forEach { calId ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = "${calId.ecuName}: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                            Text(
                                text = calId.calibrationId,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnBackground
                            )
                        }
                    }
                } else {
                    Text(
                        "No calibration IDs available",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        }

        // CVNs
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = StatusGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Calibration Verification Numbers",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnBackground
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (info.cvns.isNotEmpty()) {
                    info.cvns.forEach { cvn ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = "${cvn.ecuName}: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant
                            )
                            Text(
                                text = cvn.cvn,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnBackground
                            )
                        }
                    }
                } else {
                    Text(
                        "No CVNs available",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }
        }
    }
}
