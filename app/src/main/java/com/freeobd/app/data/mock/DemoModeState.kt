package com.freeobd.app.data.mock

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
    private var mockObdRepository: MockOBDRepository? = null

    @Volatile
    var realObdRepository: OBDRepository? = null

    /** The currently active OBD repository (mock or real). */
    val current: OBDRepository?
        get() = if (isDemoMode) mockObdRepository else realObdRepository

    /** Enable demo mode and create the mock repository. */
    fun enableDemoMode() {
        if (mockObdRepository == null) {
            mockObdRepository = MockOBDRepository()
        }
        isDemoMode = true
    }

    /** Disable demo mode and return to real repository. */
    fun disableDemoMode() {
        isDemoMode = false
    }
}
