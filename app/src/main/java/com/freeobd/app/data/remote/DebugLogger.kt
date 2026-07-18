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

package com.freeobd.app.data.remote

import android.content.Context
import com.freeobd.app.domain.model.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * In-memory log buffer for the Debug Console.
 *
 * When [enabled] is true, [tx] and [rx] calls append entries to [logs].
 * Errors are always recorded regardless of the enabled flag.
 * Call [saveToFile] to persist the current buffer to external storage.
 */
object DebugLogger {

    private val _logs = MutableStateFlow<List<DebugLog>>(emptyList())
    val logs: StateFlow<List<DebugLog>> = _logs.asStateFlow()

    /** Master switch — when false, TX/RX entries are silently dropped. */
    @Volatile var enabled: Boolean = false
        private set

    private val _enabledFlow = MutableStateFlow(false)
    val enabledFlow: StateFlow<Boolean> = _enabledFlow.asStateFlow()

    private var nextId: Long = 0

    fun setEnabled(value: Boolean) {
        enabled = value
        _enabledFlow.value = value
        if (!value) clear()
    }

    fun tx(command: String) {
        append(DebugLog(nextId++, System.currentTimeMillis(), DebugLog.Type.TX, command))
    }

    fun rx(response: String) {
        append(DebugLog(nextId++, System.currentTimeMillis(), DebugLog.Type.RX, response))
    }

    fun error(message: String) {
        append(DebugLog(nextId++, System.currentTimeMillis(), DebugLog.Type.ERROR, message))
    }

    fun clear() {
        _logs.value = emptyList()
        nextId = 0
    }

    /**
     * Save the current log buffer to a timestamped file in the app's
     * external files directory. Returns the file on success.
     */
    fun saveToFile(context: Context): Result<File> {
        return runCatching {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "obd_debug_$timestamp.txt")

            file.printWriter().use { writer ->
                val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                _logs.value.forEach { entry ->
                    val time = format.format(Date(entry.timestamp))
                    val tag = when (entry.type) {
                        DebugLog.Type.TX -> "TX"
                        DebugLog.Type.RX -> "RX"
                        DebugLog.Type.ERROR -> "ERR"
                    }
                    writer.println("$time [$tag] ${entry.message}")
                }
            }
            file
        }
    }

    private fun append(log: DebugLog) {
        _logs.value = _logs.value + log
    }
}
