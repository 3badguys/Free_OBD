package com.freeobd.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.freeobd.app.presentation.navigation.AppNavHost
import com.freeobd.app.presentation.theme.FreeOBDTheme

/**
 * Single-activity host for the Free OBD app.
 *
 * Uses Jetpack Compose for all UI rendering and
 * Compose Navigation for screen transitions.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FreeOBDTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}
