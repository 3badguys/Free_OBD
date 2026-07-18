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

package com.freeobd.app.presentation.dtc_lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freeobd.app.data.local.entity.DtcEntity
import com.freeobd.app.presentation.theme.*
import org.koin.androidx.compose.koinViewModel

/**
 * DTC Lookup — offline reference page for browsing and searching
 * DTC fault code definitions from the bundled dtc_codes.db.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtcLookupScreen(
    onNavigateBack: () -> Unit,
    viewModel: DtcLookupViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedEntity by viewModel.selectedEntity.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DTC Lookup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface, titleContentColor = OnBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onEvent(DtcLookupEvent.Search(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by code or description…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onEvent(DtcLookupEvent.Search("")) }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = OnSurfaceVariant
                )
            )

            Spacer(Modifier.height(8.dp))

            // Result count
            if (!uiState.isLoading) {
                Text(
                    "${uiState.totalItems} code(s) found",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            // Content area
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
                uiState.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No results found", color = OnSurface)
                    }
                }
                else -> {
                    // DTC list
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.items, key = { it.code }) { entity ->
                            DtcLookupItem(
                                entity = entity,
                                onClick = {
                                    viewModel.onEvent(DtcLookupEvent.ShowDetail(entity))
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Pagination controls (page size, jump, prev/next)
                    PaginationBar(
                        currentPage = uiState.currentPage,
                        totalPages = uiState.totalPages,
                        pageSize = uiState.pageSize,
                        onPrev = { viewModel.onEvent(DtcLookupEvent.PrevPage) },
                        onNext = { viewModel.onEvent(DtcLookupEvent.NextPage) },
                        onPageSizeChange = { viewModel.onEvent(DtcLookupEvent.SetPageSize(it)) },
                        onGoToPage = { viewModel.onEvent(DtcLookupEvent.GoToPage(it)) }
                    )
                }
            }
        }

        // Detail dialog
        if (selectedEntity != null) {
            DtcReferenceDialog(
                entity = selectedEntity!!,
                onDismiss = { viewModel.onEvent(DtcLookupEvent.DismissDetail) }
            )
        }
    }
}

@Composable
private fun DtcLookupItem(entity: DtcEntity, onClick: () -> Unit) {
    val clickModifier = Modifier.clickable(onClick = onClick)

    Card(
        modifier = Modifier.fillMaxWidth().then(clickModifier),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entity.code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = StatusYellow,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    entity.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface,
                    maxLines = 2
                )
                Text(
                    expandCategory(entity.category),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceVariant)
        }
    }
}

@Composable
private fun DtcReferenceDialog(entity: DtcEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = StatusBlue)
                Spacer(Modifier.width(12.dp))
                Text(entity.code, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    entity.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        "Category",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(
                        expandCategory(entity.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnBackground,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        containerColor = Surface
    )
}

@Composable
private fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    pageSize: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPageSizeChange: (Int) -> Unit,
    onGoToPage: (Int) -> Unit
) {
    var jumpInput by remember { mutableStateOf("") }
    var pageSizeExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Page size dropdown (left)
        Box {
            Row(
                modifier = Modifier
                    .clickable { pageSizeExpanded = true }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$pageSize",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.ArrowDropDown, null,
                    modifier = Modifier.size(12.dp),
                    tint = Primary
                )
            }
            DropdownMenu(
                expanded = pageSizeExpanded,
                onDismissRequest = { pageSizeExpanded = false }
            ) {
                DTC_PAGE_SIZE_OPTIONS.forEach { size ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "$size per page",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (size == pageSize) FontWeight.Bold else FontWeight.Normal,
                                color = if (size == pageSize) Primary else OnSurface
                            )
                        },
                        onClick = {
                            onPageSizeChange(size)
                            pageSizeExpanded = false
                        },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(6.dp))

        // Prev
        IconButton(
            onClick = onPrev,
            enabled = currentPage > 1,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.ChevronLeft, "Previous",
                tint = if (currentPage > 1) Primary else OnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        // Page info
        Text(
            "$currentPage/$totalPages",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurface
        )

        // Page jump — BasicTextField for minimal height
        BasicTextField(
            value = jumpInput,
            onValueChange = { newVal ->
                if (newVal.all { it.isDigit() } && newVal.length <= 4) {
                    jumpInput = newVal
                }
            },
            modifier = Modifier
                .width(36.dp)
                .heightIn(min = 22.dp)
                .border(1.dp, OnSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .background(SurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.labelSmall.copy(
                color = OnBackground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.Center) {
                    if (jumpInput.isEmpty()) {
                        Text(
                            "…",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )

        // Go
        IconButton(
            onClick = {
                val page = jumpInput.toIntOrNull()
                if (page != null && page >= 1 && page <= totalPages) {
                    onGoToPage(page)
                    jumpInput = ""
                }
            },
            enabled = jumpInput.isNotEmpty(),
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                Icons.Default.PlayArrow, "Go",
                tint = if (jumpInput.isNotEmpty()) Primary else OnSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
        }

        // Next
        IconButton(
            onClick = onNext,
            enabled = currentPage < totalPages,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.ChevronRight, "Next",
                tint = if (currentPage < totalPages) Primary else OnSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Map single-letter category codes to full names (matching the DTC domain model). */
private fun expandCategory(cat: String): String = when (cat.uppercase()) {
    "P" -> "POWERTRAIN"
    "B" -> "BODY"
    "C" -> "CHASSIS"
    "U" -> "NETWORK"
    else -> cat
}
