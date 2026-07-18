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

package com.freeobd.app.data.remote

import kotlinx.coroutines.delay

/**
 * Executes the ELM327 initialization sequence using raw AT commands.
 *
 *  1. ATZ            — Full reset (clears previous session state)
 *  2. ATE0           — Disable command echo
 *  3. ATL0           — Disable line feeds
 *  4. AT+VERSION     — Extended version info; auto-detects Yuming crypto handshake
 *  4.5. AT+SETCRYPT  — Auto-computed for Yuming adapters (non-critical)
 *  5. ATSPx          — Protocol selection
 *  6. ATH0/ATH1      — Always explicitly set: ATH0 (default) or ATH1 when showResponseHeaders
 *  7. ATSH           — ECU header address (optional)
 */
class ELM327Initializer(
    private val commandQueue: ObdCommandQueue
) {
    suspend fun initialize(
        protocol: String = "ATSP0",
        ecuAddress: String? = null,
        showResponseHeaders: Boolean = false
    ): Result<Unit> {
        val steps = buildInitSteps(protocol, ecuAddress, showResponseHeaders)

        steps.forEachIndexed { index, step ->
            val result = commandQueue.sendRaw(step.command)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                if (step.critical) {
                    return Result.failure(
                        ELM327InitException(
                            "ELM327 init failed at step ${index + 1}/${steps.size} " +
                                "(${step.description}): ${error?.message ?: "unknown error"}",
                            error
                        )
                    )
                }
                // Non-critical step failure (e.g. ATRV on adapters that
                // don't support it) — skip and continue.
            }

            // After AT+VERSION, auto-detect Yuming crypto handshake.
            // The crypt: challenge is embedded in the version response and
            // changes on every connection, so the key must be computed at runtime.
            if (step.command == "AT+VERSION") {
                val key = result.getOrNull()?.let { response ->
                    if (YMOBDCrypto.isYumingAdapter(response)) {
                        YMOBDCrypto.extractCryptChallenge(response)?.let { challenge ->
                            YMOBDCrypto.generateKey(challenge)
                        }
                    } else null
                }
                if (!key.isNullOrBlank()) {
                    commandQueue.sendRaw("AT+SETCRYPT$key")
                    delay(300L)
                }
            }

            delay(step.postDelayMs)
        }
        return Result.success(Unit)
    }

    private fun buildInitSteps(
        protocol: String,
        ecuAddress: String?,
        showResponseHeaders: Boolean
    ): List<InitStep> {
        val steps = mutableListOf<InitStep>()
        val proto = if (protocol in VALID_PROTOCOLS) protocol else "ATSP0"

        // Step 1: ATZ — full reset to clear previous session state.
        steps.add(InitStep("ATZ (reset)", "ATZ", 500L))

        // Step 2: Disable echo
        steps.add(InitStep("ATE0 (disable echo)", "ATE0", 300L))

        // Step 3: Disable line feed
        steps.add(InitStep("ATL0 (disable line feed)", "ATL0", 200L))

        // Step 4: Read extended version info.
        steps.add(InitStep("AT+VERSION", "AT+VERSION", 200L, critical = false))

        // Step 5: Select protocol
        steps.add(
            InitStep(
                "$proto (protocol select)",
                proto,
                if (proto == "ATSP0") 500L else 300L
            )
        )

        // Step 6: Response headers — always explicitly set, regardless of protocol.
        // ATH0 = clean data (default), ATH1 = show CAN headers in responses.
        steps.add(InitStep(
            if (showResponseHeaders) "ATH1 (show headers)" else "ATH0 (hide headers)",
            if (showResponseHeaders) "ATH1" else "ATH0",
            200L
        ))

        // Step 7 (optional): Set ECU header address
        if (!ecuAddress.isNullOrBlank()) {
            steps.add(InitStep("ATSH $ecuAddress", "ATSH$ecuAddress", 200L))
        }

        return steps
    }

    private data class InitStep(
        val description: String,
        val command: String,
        val postDelayMs: Long,
        val critical: Boolean = true
    )

    companion object {
        private val VALID_PROTOCOLS = setOf(
            "ATSP0", "ATSP1", "ATSP2", "ATSP3", "ATSP4",
            "ATSP5", "ATSP6", "ATSP7", "ATSP8", "ATSP9",
            "ATSPA", "ATSPB", "ATSPC"
        )

    }
}

class ELM327InitException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
