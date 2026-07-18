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
 * The actual OBD protocol negotiated between the ELM327 adapter and the vehicle.
 *
 * Obtained via ATDP (description) and ATDPN (numeric identifier) after
 * the adapter has auto-detected or been set to a specific protocol.
 *
 * @property description Human-readable protocol name from ATDP,
 *   e.g. "ISO 15765-4 CAN (11 bit ID, 500 kbaud)".
 * @property number Single-character numeric identifier from ATDPN,
 *   e.g. "A0" for auto-detected, "1" = SAE J1850 PWM, "6" = ISO 15765-4 CAN.
 */
data class ProtocolInfo(
    val description: String,
    val number: String
)
