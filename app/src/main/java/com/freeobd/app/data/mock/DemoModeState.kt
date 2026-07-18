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

package com.freeobd.app.data.mock

import com.freeobd.app.data.local.AppDatabase
import com.freeobd.app.domain.repository.OBDRepository

/**
 * Singleton holder for the current OBD repository, supporting
 * transparent switching between real and mock implementations.
 *
 * Set by [BluetoothViewModel] when demo mode is toggled.
 * Downstream ViewModels read [current] to get the active repo.
 */
object DemoModeState {

    @Volatile
    var isDemoMode: Boolean = false
        private set

    @Volatile
    var showResponseHeaders: Boolean = true

    @Volatile
    private var mockObdRepository: MockOBDRepository? = null

    @Volatile
    var realObdRepository: OBDRepository? = null

    /** The currently active OBD repository (mock or real). */
    val current: OBDRepository?
        get() = if (isDemoMode) mockObdRepository else realObdRepository

    /** Enable demo mode and create the mock repository. */
    fun enableDemoMode(database: AppDatabase) {
        if (mockObdRepository == null) {
            mockObdRepository = MockOBDRepository(database)
        }
        isDemoMode = true
    }

    /** Disable demo mode and return to real repository. */
    fun disableDemoMode() {
        isDemoMode = false
    }
}
