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

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freeobd.app.data.remote.DebugLogger
import com.freeobd.app.domain.model.DebugLog
import com.freeobd.app.domain.repository.OBDRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DebugViewModel(
    private val obdRepository: OBDRepository
) : ViewModel() {

    val logs: StateFlow<List<DebugLog>> = DebugLogger.logs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isEnabled: Boolean get() = DebugLogger.enabled
    val enabledFlow = DebugLogger.enabledFlow

    fun clear() {
        DebugLogger.clear()
    }

    fun saveToFile(context: Context): Result<File> {
        return DebugLogger.saveToFile(context)
    }

    fun saveToUri(context: Context, uri: Uri): Result<Unit> {
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val lines = DebugLogger.logs.value
                val df = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                lines.forEach { entry ->
                    val time = df.format(java.util.Date(entry.timestamp))
                    val tag = when (entry.type) {
                        DebugLog.Type.TX -> "TX"
                        DebugLog.Type.RX -> "RX"
                        DebugLog.Type.ERROR -> "ERR"
                    }
                    out.write("$time [$tag] ${entry.message}\n".toByteArray())
                }
            } ?: throw IllegalStateException("Cannot open output stream")
        }
    }

    fun sendCommand(command: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                obdRepository.sendRawCommand(command)
            }
        }
    }
}
