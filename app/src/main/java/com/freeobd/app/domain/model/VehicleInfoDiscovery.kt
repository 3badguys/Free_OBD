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

package com.freeobd.app.domain.model

/**
 * Result of Mode 09 PID 00 (InfoType bitmap discovery).
 *
 * @property rawHex Raw ELM327 hex response (e.g. "49 00 54 02").
 * @property supportedTypes Set of supported InfoType IDs.
 */
data class VehicleInfoDiscovery(
    val rawHex: String,
    val supportedTypes: Set<Int>
)
