package com.freeobd.app.data.remote

import kotlinx.coroutines.delay

/**
 * Executes the ELM327 initialization sequence using raw AT commands.
 *
 *  1. ATZ            — Full reset (clears previous session state)
 *  2. ATE0           — Disable command echo
 *  3. ATL0           — Disable line feeds
 *  4. ATRV           — Voltage check (non-critical)
 *  5. ATI            — Firmware version (non-critical, useful for debugging)
 *  6. AT+SETCRYPTF   — Crypto key for STM32 clone adapters (optional, non-critical)
 *  7. ATSPx          — Protocol selection
 *  8. ATH1           — Enable CAN headers (CAN only, skipped for K-line)
 *  9. ATSH           — ECU header address (optional)
 */
class ELM327Initializer(
    private val commandQueue: ObdCommandQueue
) {
    suspend fun initialize(
        protocol: String = "ATSP0",
        ecuAddress: String? = null,
        cryptoKey: String? = null
    ): Result<Unit> {
        val steps = buildInitSteps(protocol, ecuAddress, cryptoKey)

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
                // Non-critical step failure (e.g. ATRV, AT+SETCRYPTF
                // on adapters that don't support it) — skip and continue.
            }
            delay(step.postDelayMs)
        }
        return Result.success(Unit)
    }

    private fun buildInitSteps(
        protocol: String,
        ecuAddress: String?,
        cryptoKey: String?
    ): List<InitStep> {
        val steps = mutableListOf<InitStep>()
        val proto = if (protocol in VALID_PROTOCOLS) protocol else "ATSP0"

        // Step 1: ATZ — full reset to clear previous session state.
        steps.add(InitStep("ATZ (reset)", "ATZ", 500L))

        // Step 2: Disable echo
        steps.add(InitStep("ATE0 (disable echo)", "ATE0", 300L))

        // Step 3: Disable line feed
        steps.add(InitStep("ATL0 (disable line feed)", "ATL0", 200L))

        // Step 4: Read voltage — quick check that the OBD port has power.
        // Non-critical; failure here doesn't abort init.
        steps.add(InitStep("ATRV (voltage check)", "ATRV", 200L, critical = false))

        // Step 5: Read firmware version — useful for debugging adapter issues.
        steps.add(InitStep("ATI (firmware info)", "ATI", 200L, critical = false))

        // Step 5b: Read extended version info (debug console only, not displayed in UI).
        steps.add(InitStep("AT+VERSION", "AT+VERSION", 200L, critical = false))

        // Step 6: Set crypto key BEFORE protocol selection.
        // STM32-based ELM327 clones need this handshake before they will
        // relay OBD data. The key is user-configurable in Advanced options.
        if (!cryptoKey.isNullOrBlank()) {
            steps.add(
                InitStep(
                    "AT+SETCRYPTF (crypto key)",
                    "AT+SETCRYPTF $cryptoKey",
                    300L,
                    critical = false
                )
            )
        }

        // Step 7: Select protocol
        steps.add(
            InitStep(
                "$proto (protocol select)",
                proto,
                if (proto == "ATSP0") 500L else 300L
            )
        )

        // Step 8: Enable CAN headers (CAN-only; skipped for K-line)
        if (proto !in KLINE_PROTOCOLS) {
            steps.add(InitStep("ATH1 (enable CAN headers)", "ATH1", 200L))
        }

        // Step 9 (optional): Set ECU header address
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
            "ATSP5", "ATSP6", "ATSP7", "ATSP8", "ATSP9"
        )

        /** K-line based protocols (ISO 9141-2 / KWP2000). */
        private val KLINE_PROTOCOLS = setOf("ATSP3", "ATSP4", "ATSP5")
    }
}

class ELM327InitException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
