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
 * Result of Mode 02 PID 00 (Freeze Frame PID bitmap discovery).
 *
 * @property rawHex Raw ELM327 hex response (e.g. "42 00 BE 1F A8 13").
 * @property supportedPids Set of supported PID IDs in the current freeze frame.
 */
data class FreezeFrameDiscovery(
    val rawHex: String,
    val supportedPids: Set<Int>
)
