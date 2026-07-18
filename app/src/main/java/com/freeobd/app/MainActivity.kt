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
