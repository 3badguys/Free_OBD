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

package com.freeobd.app.di

import com.freeobd.app.data.local.AppDatabase
import com.freeobd.app.data.mock.DemoModeState
import com.freeobd.app.data.repository.BluetoothRepositoryImpl
import com.freeobd.app.data.repository.OBDRepositoryImpl
import com.freeobd.app.domain.repository.BluetoothRepository
import com.freeobd.app.domain.repository.OBDRepository
import com.freeobd.app.domain.usecase.*
import com.freeobd.app.presentation.bluetooth.BluetoothViewModel
import com.freeobd.app.presentation.dashboard.DashboardViewModel
import com.freeobd.app.presentation.debug.DebugViewModel
import com.freeobd.app.presentation.dtc.DtcViewModel
import com.freeobd.app.presentation.dtc_lookup.DtcLookupViewModel
import com.freeobd.app.presentation.freezeframe.FreezeFrameViewModel
import com.freeobd.app.presentation.livedata.LiveDataViewModel
import com.freeobd.app.presentation.vehicle.VehicleViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin dependency injection module.
 *
 * Wiring order:
 *   Database → DAOs
 *   Repositories → Use Cases → ViewModels
 *
 * Scopes:
 * - single: App-wide singletons (DB, repositories)
 * - factory: New instance per injection (use cases — lightweight)
 * - viewModel: Managed by the ViewModel lifecycle
 */
val appModule = module {

    // ── Database ────────────────────────────────────────────

    single { AppDatabase.getInstance(androidContext()) }

    // DAOs (derived from the database singleton)
    single { get<AppDatabase>().dtcDao() }
    single { get<AppDatabase>().pidMetadataDao() }
    single { get<AppDatabase>().vehicleProfileDao() }

    // ── Repositories ───────────────────────────────────────

    // BluetoothRepository is a singleton — manages connection state
    single<BluetoothRepository> {
        BluetoothRepositoryImpl(androidContext())
    }

    // OBDRepository — gets transport from BluetoothRepository at runtime
    single<OBDRepository> {
        val repo = OBDRepositoryImpl(
            bluetoothRepository = get(),
            database = get()
        )
        DemoModeState.realObdRepository = repo
        repo
    }

    // ── Use Cases ──────────────────────────────────────────

    factory { ConnectBluetoothUseCase(get(), get()) }
    factory { ReadLiveDataUseCase(get()) }
    factory { ReadDTCUseCase(get()) }
    // ── ViewModels ─────────────────────────────────────────

    viewModel { BluetoothViewModel(get(), get(), get(), get()) }
    viewModel { DashboardViewModel(get(), get()) }
    viewModel { DebugViewModel(get()) }
    viewModel { DtcViewModel(get(), get()) }
    viewModel { DtcLookupViewModel(get()) }
    viewModel { LiveDataViewModel(get(), get()) }
    viewModel { FreezeFrameViewModel(get(), get()) }
    viewModel { VehicleViewModel(get(), get()) }
}
