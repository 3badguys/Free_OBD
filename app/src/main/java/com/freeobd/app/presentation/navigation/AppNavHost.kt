package com.freeobd.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.freeobd.app.presentation.bluetooth.BluetoothScreen
import com.freeobd.app.presentation.dashboard.DashboardScreen
import com.freeobd.app.presentation.debug.DebugConsoleScreen
import com.freeobd.app.presentation.dtc.DtcScreen
import com.freeobd.app.presentation.dtc_lookup.DtcLookupScreen
import com.freeobd.app.presentation.freezeframe.FreezeFrameScreen
import com.freeobd.app.presentation.livedata.LiveDataScreen
import com.freeobd.app.presentation.vehicle.VehicleScreen
import org.koin.androidx.compose.koinViewModel

/**
 * Top-level navigation host for the Free OBD app.
 *
 * Single-activity architecture with Compose Navigation.
 * The Bluetooth screen is the start destination — all other features
 * require an active OBD connection.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = NavRoutes.Bluetooth.route
) {
    // Guard against rapid double-taps — popping an already-empty stack
    // navigates to a blank screen.
    val popBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Bluetooth connection screen (home)
        composable(NavRoutes.Bluetooth.route) {
            BluetoothScreen(
                onNavigateToDashboard = {
                    navController.navigate(NavRoutes.Dashboard.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToLiveData = {
                    navController.navigate(NavRoutes.LiveData.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToFreezeFrame = {
                    navController.navigate(NavRoutes.FreezeFrame.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDTC = {
                    navController.navigate(NavRoutes.DTC.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToVehicleInfo = {
                    navController.navigate(NavRoutes.VehicleInfo.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDtcLookup = {
                    navController.navigate(NavRoutes.DtcLookup.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDebugConsole = {
                    navController.navigate(NavRoutes.DebugConsole.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // Live data dashboard
        composable(NavRoutes.Dashboard.route) {
            DashboardScreen(
                onNavigateBack = popBack
            )
        }

        // Mode 01 Live Data
        composable(NavRoutes.LiveData.route) {
            LiveDataScreen(
                onNavigateBack = popBack
            )
        }

        // Freeze Frame data (Mode 02)
        composable(NavRoutes.FreezeFrame.route) {
            FreezeFrameScreen(
                onNavigateBack = popBack
            )
        }

        // Diagnostic Trouble Codes
        composable(NavRoutes.DTC.route) {
            DtcScreen(
                onNavigateBack = popBack
            )
        }

        // Vehicle Information
        composable(NavRoutes.VehicleInfo.route) {
            VehicleScreen(
                onNavigateBack = popBack
            )
        }

        // DTC Lookup (offline reference)
        composable(NavRoutes.DtcLookup.route) {
            DtcLookupScreen(
                onNavigateBack = popBack
            )
        }

        // Debug Console
        composable(NavRoutes.DebugConsole.route) {
            DebugConsoleScreen(
                onNavigateBack = popBack
            )
        }
    }
}
