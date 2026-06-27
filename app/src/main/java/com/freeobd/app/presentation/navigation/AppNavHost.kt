package com.freeobd.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.freeobd.app.presentation.bluetooth.BluetoothScreen
import com.freeobd.app.presentation.dashboard.DashboardScreen
import com.freeobd.app.presentation.dtc.DtcScreen
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
                onNavigateToDTC = {
                    navController.navigate(NavRoutes.DTC.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToVehicleInfo = {
                    navController.navigate(NavRoutes.VehicleInfo.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // Live data dashboard
        composable(NavRoutes.Dashboard.route) {
            DashboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Diagnostic Trouble Codes
        composable(NavRoutes.DTC.route) {
            DtcScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Vehicle Information
        composable(NavRoutes.VehicleInfo.route) {
            VehicleScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
